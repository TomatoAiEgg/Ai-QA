# Susan AI - 增加 RAG 知识库功能教程

## 📋 目录

1. [功能概述](#功能概述)
2. [环境准备](#环境准备)
3. [后端开发：优化 RAG 服务逻辑](#后端开发优化-rag-服务逻辑)
4. [前端开发：增加功能开关与交互](#前端开发增加功能开关与交互)
5. [系统监控：增加全链路日志](#系统监控增加全链路日志)
6. [测试运行](#测试运行)
7. [总结](#总结)

---

## 功能概述

### 功能简介

本教程将在 Susan AI 项目（已集成 Ollama 和 Spring AI）的基础上，增加完整的 RAG（检索增强生成）功能。实现包括：

- ✅ **知识库检索**：基于用户问题检索向量数据库中的相关文档片段。
- ✅ **智能问答**：将检索到的上下文注入到 Prompt 中，让 AI 基于私有知识库回答。
- ✅ **前端开关**：用户可自由切换“普通对话”和“RAG 对话”模式。
- ✅ **全链路监控**：完整记录用户提问、检索结果、Prompt 构建及 AI 响应日志。

### 技术要点

- **Spring AI**: 核心 AI 框架，处理 ChatClient 和 VectorStore。
- **RAG (Retrieval-Augmented Generation)**: 检索增强生成技术。
- **Vector Store**: 向量数据库检索（本项目使用 PgVector）。
- **Prompt Engineering**: 提示词工程，分离 System Prompt 和 User Prompt。
- **Flux**: 响应式编程，处理流式 AI 响应。

### 功能目标

通过本教程，你将学会：

- ✅ 实现 RAG 核心检索与生成逻辑。
- ✅ 优化 Prompt 结构，减少 AI 幻觉。
- ✅ 修改前端页面，增加功能控制开关。
- ✅ 添加结构化日志，监控 AI 运行状态。

---

## 环境准备

### 前置条件

确保你已经完成了基础的 Susan AI 项目搭建，并且：
- ✅ 本地已安装 Ollama 并运行模型。
- ✅ PostgreSQL (PgVector) 数据库已启动。
- ✅ `application.yml` 中已配置好向量数据库连接。

### 检查当前项目状态

```bash
# 确认项目可以正常启动
mvn spring-boot:run

# 确认向量数据库表 vector_store 存在
```

---

## 后端开发：优化 RAG 服务逻辑

### 步骤 1: 修改 AiService 类

我们需要在 `AiService` 中实现 RAG 的核心逻辑：检索 -> 拼接 -> 提问。

**文件**: `src/main/java/cn/net/susan/ai/service/AiService.java`

1.  **定义 Prompt 模板**：
    将 System Prompt（系统指令）和 User Prompt（上下文+问题）分离，提高模型遵循度。

    ```java
    // RAG 系统提示词
    private static final String RAG_SYSTEM_PROMPT = """
            你是一个智能助手，请根据用户提供的上下文信息回答问题。
            如果不确定或上下文不包含相关信息，请直接回答不知道，不要编造内容。
            """;

    // RAG 用户提示词模板
    private static final String RAG_USER_PROMPT_TEMPLATE = """
            【上下文信息】：
            %s
            
            【用户问题】：
            %s
            """;
    ```

2.  **实现 chatByRag 方法**：
    增加相似度阈值过滤（0.6），防止检索到无关内容误导 AI。

    ```java
    /**
     * RAG 对话接口
     *
     * @param question 用户问题
     * @return 流式返回的字符串
     */
    public Flux<String> chatByRag(String question) {
        // 1. 记录用户问题
        log.info("收到 RAG 对话请求，问题: {}", question);

        // 2. 检索向量数据库，增加相似度阈值过滤 (0.6)
        List<Document> documents = vectorStore.similaritySearch(
                SearchRequest.query(question).withTopK(5).withSimilarityThreshold(0.6));
        
        // 3. 空结果处理
        if (documents.isEmpty()) {
            log.info("未检索到相关文档片段，直接回答不知道");
            return Flux.just("抱歉，知识库中没有找到相关信息。");
        }

        // 4. 记录检索详细日志
        log.info("检索到 {} 个相关文档片段:", documents.size());
        for (int i = 0; i < documents.size(); i++) {
            Document doc = documents.get(i);
            String filename = (String) doc.getMetadata().getOrDefault("filename", "unknown");
            // 截取部分内容打印，避免日志过长
            String contentSnippet = doc.getContent().length() > 100 ? 
                    doc.getContent().substring(0, 100) + "..." : doc.getContent();
            log.info("片段 {}: filename={}, content={}", i + 1, filename, contentSnippet);
        }

        // 5. 构建上下文
        String context = documents.stream()
                .map(Document::getContent)
                .collect(Collectors.joining("\n\n"));

        // 6. 构建用户 Prompt
        String userPrompt = String.format(RAG_USER_PROMPT_TEMPLATE, context, question);
        log.info("构造的 RAG 用户提示词:\n{}", userPrompt);

        // 7. 调用大模型 (增加响应日志)
        StringBuilder responseBuilder = new StringBuilder();
        return chatClient.prompt()
                .system(RAG_SYSTEM_PROMPT) // 注入系统指令
                .user(userPrompt)          // 注入上下文和问题
                .stream()
                .content()
                .doOnNext(responseFragment -> {
                    responseBuilder.append(responseFragment);
                })
                .doOnComplete(() -> {
                    log.info("RAG 对话响应完成，完整回复内容: {}", responseBuilder.toString());
                });
    }
    ```

3.  **优化普通对话方法 (chatByStream)**：
    同样为普通对话增加日志，保持监控的一致性。

    ```java
    public Flux<String> chatByStream(String question) {
        log.info("收到普通 AI 对话请求，问题: {}", question);
        StringBuilder fullReply = new StringBuilder();
        
        return ollamaIntegration.chatByStream(question)
                .flatMap(response -> {
                    // ...原有处理逻辑...
                    String reply = ...;
                    fullReply.append(reply);
                    return Flux.just(reply);
                })
                .doOnComplete(() -> {
                    log.info("普通 AI 对话响应完成，完整回复内容: {}", fullReply);
                });
    }
    ```

### 步骤 2: 确认 Controller 接口

确保 `AiController` 中暴露了 RAG 接口。

**文件**: `src/main/java/cn/net/susan/ai/controller/AiController.java`

```java
@PostMapping(value = "/ai/chatByRag", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
public Flux<String> chatByRag(@RequestBody String question) {
    return aiService.chatByRag(question);
}
```

---

## 前端开发：增加功能开关与交互

### 步骤 1: 修改 chat.html 样式

在聊天框上方增加一个美观的开关控件。

**文件**: `src/main/resources/templates/chat.html`

在 `<style>` 标签中添加以下 CSS：

```css
/* 聊天选项栏 */
.chat-options {
    display: flex;
    align-items: center;
    gap: 12px;
    padding: 0 24px;
    background: #f8fafc;
    border-bottom: 1px solid #edf2f7;
    height: 48px;
}

/* 开关容器 */
.switch-container {
    display: flex;
    align-items: center;
    gap: 8px;
    font-size: 0.9rem;
    color: #4a5568;
    cursor: pointer;
    user-select: none;
}

/* 开关本体 */
.switch {
    position: relative;
    display: inline-block;
    width: 40px;
    height: 22px;
}

.switch input {
    opacity: 0;
    width: 0;
    height: 0;
}

/* 滑块样式 */
.slider {
    position: absolute;
    cursor: pointer;
    top: 0;
    left: 0;
    right: 0;
    bottom: 0;
    background-color: #cbd5e0;
    transition: .3s;
    border-radius: 22px;
}

.slider:before {
    position: absolute;
    content: "";
    height: 18px;
    width: 18px;
    left: 2px;
    bottom: 2px;
    background-color: white;
    transition: .3s;
    border-radius: 50%;
    box-shadow: 0 2px 4px rgba(0,0,0,0.1);
}

input:checked + .slider {
    background-color: var(--primary-color);
}

input:checked + .slider:before {
    transform: translateX(18px);
}
```

### 步骤 2: 修改 HTML 结构

在 `chat-header` 和 `chat-messages` 之间插入开关 HTML 代码。

```html
<div class="chat-header">...</div>

<!-- 新增：RAG 开关 -->
<div class="chat-options">
    <label class="switch-container">
        <div class="switch">
            <input type="checkbox" id="ragSwitch">
            <span class="slider"></span>
        </div>
        <span>启用 RAG 知识库增强</span>
    </label>
</div>

<div class="chat-messages" id="chatMessages"></div>
```

### 步骤 3: 修改 JavaScript 逻辑

监听发送按钮，根据开关状态动态选择后端 API 接口。

```javascript
// 获取 DOM 元素
const ragSwitch = document.getElementById('ragSwitch');

// 修改发送逻辑
sendButton.addEventListener('click', async () => {
    // ... 前置校验逻辑 ...

    try {
        const typingIndicator = createTypingIndicator();

        // 核心修改：根据开关状态选择 API 接口
        const apiEndpoint = ragSwitch.checked ? '/ai/chatByRag' : '/ai/chatByOllama';
        
        // 发送请求
        const response = await fetch(apiEndpoint, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'Accept': 'text/event-stream'
            },
            // 注意：后端接收 String 类型 Body，这里直接发送字符串即可
            // 如果后端使用 @RequestBody String，Spring 会自动处理
            body: JSON.stringify(question),
        });

        typingIndicator.remove();
        await handleBotResponse(response);

    } catch (error) {
        console.error(error);
        displayMessage('bot', '暂时无法处理您的请求，请稍后再试');
    } finally {
        // ... 清理逻辑 ...
    }
});
```

---

## 系统监控：增加全链路日志

通过在 `AiService` 中添加的日志，我们可以在控制台清晰地看到 RAG 的运行过程。

### 日志示例

**1. 收到请求**
```text
INFO  c.n.s.ai.service.AiService : 收到 RAG 对话请求，问题: 苏三是谁？
```

**2. 向量检索**
```text
INFO  c.n.s.ai.service.AiService : 检索到 2 个相关文档片段:
INFO  c.n.s.ai.service.AiService : 片段 1: filename=susan_profile.txt, content=苏三是一个热爱技术的 Java 架构师...
INFO  c.n.s.ai.service.AiService : 片段 2: filename=team_intro.pdf, content=团队核心成员包括苏三...
```

**3. 构建 Prompt**
```text
INFO  c.n.s.ai.service.AiService : 构造的 RAG 用户提示词:
【上下文信息】：
苏三是一个热爱技术的 Java 架构师...

【用户问题】：
苏三是谁？
```

**4. AI 响应**
```text
INFO  c.n.s.ai.service.AiService : RAG 对话响应完成，完整回复内容: 苏三是一位资深的 Java 架构师，专注于...
```

---

## 测试运行

1.  **启动应用**：运行 `SusanAiApplication`。
2.  **访问页面**：浏览器打开 `http://localhost:8080`。
3.  **上传文档**：点击右上角“知识库管理”，上传包含特定知识的文档（如公司介绍）。
4.  **普通对话测试**：
    *   关闭 RAG 开关。
    *   问：“苏三是谁？”
    *   预期：AI 可能回答不知道，或根据通用知识瞎编。
5.  **RAG 对话测试**：
    *   开启 RAG 开关。
    *   问：“苏三是谁？”
    *   预期：AI 准确引用上传文档中的内容回答。
    *   **查看日志**：确认控制台打印了完整的检索和 Prompt 构建过程。

---

## 总结

恭喜！你已经成功为 Susan AI 增加了 RAG 知识库功能。

**核心成果**：
*   实现了基于 PgVector 的向量检索。
*   构建了专业的 RAG Prompt 模板。
*   提供了友好的前端交互开关。
*   建立了完整的日志监控体系。

接下来，你可以尝试进一步优化：
*   增加文档切片策略（Token Splitter）。
*   支持更多格式的文档解析（PDF, Word）。
*   引入重排序模型（Rerank）提高检索精度。
