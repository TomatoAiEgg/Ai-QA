# AI-QA 项目入门指南

**版本**: v1.0.2
**最后更新**: 2026-03-15

---

## 一、项目简介

AI-QA 是一个基于 **Spring AI** 的智能问答系统，支持：

- ✅ AI 对话（千问大模型）
- ✅ RAG 知识库增强（文档上传、向量检索）
- ✅ 对话历史管理
- ✅ 流式响应（打字机效果）

### 技术栈

| 类别 | 技术 |
|------|------|
| 后端框架 | Spring Boot 3.4.2 |
| AI 框架 | Spring AI 1.0.0-M5 |
| 大模型 | 阿里千问（Qwen） |
| 向量数据库 | PostgreSQL + pgvector |
| 前端 | Thymeleaf + 原生 JavaScript |
| Markdown 渲染 | marked.js + DOMPurify |

---

## 二、环境准备

### 2.1 必需软件

```
✅ Java 17+
✅ Maven 3.6+
✅ PostgreSQL 16+ (带 pgvector 扩展)
```

### 2.2 检查环境

```cmd
:: 检查 Java 版本
java -version

:: 检查 Maven
mvn -version

:: 检查 PostgreSQL（可选，使用 Docker 可跳过）
psql --version
```

---

## 三、快速启动

### 3.1 启动 PostgreSQL（Docker 方式）

```cmd
cd D:\workspace\包装项目\susan-ai
docker-compose up -d
```

**访问信息**：
- 端口：15432
- 数据库：susan_ai
- 用户：postgres
- 密码：postgres

### 3.2 配置 API 密钥

**方式一：环境变量（推荐）**
```cmd
set QWEN_API_KEY=你的千问 API 密钥
```

### 3.3 启动应用

```cmd
:: 设置环境变量
set QWEN_API_KEY=你的千问 API 密钥
set "JAVA_HOME=D:\Javasource\jdk-17.0.9"
set "PATH=%JAVA_HOME%\bin;%PATH%"

:: 进入项目
cd /d D:\workspace\包装项目\susan-ai

:: 启动
mvn spring-boot:run
```

### 3.4 访问应用

浏览器打开：**http://localhost:7000**

- AI 对话：http://localhost:7000
- RAG 知识库管理：http://localhost:7000/rag

---

## 四、项目结构详解

```
AI-QA/
├── src/
│   ├── main/
│   │   ├── java/cn/net/susan/ai/
│   │   │   ├── SusanAiApplication.java      # 主启动类
│   │   │   ├── config/                       # 配置类
│   │   │   │   ├── ChatMemoryConfiguration.java  # 对话内存配置
│   │   │   │   └── DocumentSplitterConfiguration.java  # 文档切片器配置
│   │   │   ├── controller/                   # 控制器（API 接口）
│   │   │   │   └── AiController.java         # AI 对话、知识库管理接口
│   │   │   ├── service/                      # 服务层（业务逻辑）
│   │   │   │   ├── AiService.java            # AI 对话服务
│   │   │   │   ├── ConversationService.java  # 对话管理服务
│   │   │   │   └── KnowledgeBaseService.java # 知识库服务
│   │   │   ├── integration/                  # 外部集成
│   │   │   │   └── QwenIntegration.java      # 千问模型集成
│   │   │   └── repository/                   # 数据访问层
│   │   │       └── ConversationRepository.java   # 对话数据访问
│   │   └── resources/
│   │       ├── application.properties        # 配置文件
│   │       └── templates/                    # 前端页面
│   │           ├── chat.html                 # AI 对话页面
│   │           └── rag.html                  # 知识库管理页面
│   └── test/                                 # 测试代码
├── docs/                                     # 文档目录
│   ├── 项目记录.md                            # 项目变更记录
│   └── 入门指南.md                            # 本文件
├── docker-compose.yml                        # Docker 配置
├── pom.xml                                   # Maven 依赖
└── README.md                                 # 项目说明
```

---

## 五、核心功能说明

### 5.1 AI 对话流程

```
用户提问 → Controller → Service → QwenIntegration → 千问 API
                              ↓
                        保存对话记录到数据库
```

**关键代码位置**：
- 接口：`AiController.chat()`
- 服务：`AiService.chatByStream()`
- 集成：`QwenIntegration.chatByStream()`

### 5.2 RAG 知识库流程

```
用户提问 → 向量检索 → 检索相关文档 → 构建提示词 → 千问 API → 返回答案
           (pgvector)
```

**关键代码位置**：
- 接口：`AiController.chatByRag()`
- 服务：`AiService.chatByRag()`
- 上传：`KnowledgeBaseService.uploadDocument()`

### 5.3 对话管理

- 创建对话：`POST /ai/conversations`
- 列出对话：`GET /ai/conversations`
- 添加消息：`ConversationService.addUserMessage()`
- 历史记录：`GET /ai/conversations/{id}/messages`

---

## 六、API 接口一览

| 接口 | 方法 | 说明 |
|------|------|------|
| `/ai/chat` | POST | AI 对话（流式） |
| `/ai/chatByRag` | POST | RAG 知识库对话（流式） |
| `/ai/upload` | POST | 上传文档到知识库 |
| `/ai/documents` | GET | 获取文档列表 |
| `/ai/preview-chunks` | POST | 预览文档切片效果 |
| `/ai/conversations` | GET/POST | 列出/创建对话 |
| `/ai/conversations/{id}` | DELETE | 删除对话 |
| `/ai/conversations/{id}/title` | PUT | 修改对话标题 |
| `/ai/conversations/{id}/messages` | GET | 获取对话消息 |

---

## 七、常见问题

### Q1: 401 API Key 错误

**错误信息**：
```
401 - {"error":{"message":"Incorrect API key provided."}}
```

**解决方案**：
1. 确认 `QWEN_API_KEY` 环境变量已设置
2. 检查 API 密钥格式：`sk-xxxxxxxx`
3. 重启应用

### Q2: 数据库连接失败

**错误信息**：
```
Connection refused to localhost:5432
```

**解决方案**：
```cmd
:: 启动 PostgreSQL 容器
docker-compose up -d

:: 检查容器状态
docker ps
```

### Q3: 前端页面不显示响应

**检查步骤**：
1. 打开浏览器 F12 控制台
2. 查看 `[DEBUG]` 日志
3. 检查是否有 `DOMPurify is not defined` 错误
4. 确认 CDN 资源加载成功

---

## 八、开发指南

### 8.1 添加新的 AI 模型

1. 在 `integration/` 目录创建新集成类
2. 在 `AiService` 中添加模型选择逻辑
3. 在 `application.properties` 添加配置

### 8.2 自定义提示词

编辑 `AiService.java` 中的提示词模板：

```java
private static final String RAG_SYSTEM_PROMPT = "你的自定义提示词";
private static final String NORMAL_SYSTEM_PROMPT = "你的自定义提示词";
```

### 8.3 修改前端页面

编辑 `src/main/resources/templates/` 目录下的 HTML 文件，修改后刷新页面即可（无需重启）。

---

## 九、调试技巧

### 9.1 后端日志

启动时添加调试参数：
```cmd
mvn spring-boot:run -Dspring-boot.run.jvmArguments="-Ddebug=true"
```

### 9.2 前端日志

页面已内置 `[DEBUG]` 日志，打开 F12 控制台查看：
- 响应状态
- 数据块接收
- Markdown 渲染

### 9.3 数据库查看

```cmd
:: 连接数据库
docker exec -it susan-ai-pgvector psql -U postgres -d susan_ai

:: 查看对话表
\dt

:: 查询对话记录
SELECT * FROM conversations;
```

---

## 附录：快速命令参考

```cmd
:: 启动 PostgreSQL
docker-compose up -d

:: 停止 PostgreSQL
docker-compose down

:: 启动应用（完整命令）
set QWEN_API_KEY=你的千问 API 密钥
set "JAVA_HOME=D:\Javasource\jdk-17.0.9"
set "PATH=%JAVA_HOME%\bin;%PATH%"
cd /d D:\workspace\包装项目\susan-ai
mvn spring-boot:run

:: 编译项目
mvn clean compile -DskipTests

:: 打包项目
mvn clean package -DskipTests
```

---

**祝你学习愉快！** 🚀

如有问题，请查看控制台日志或查阅文档。
