package cn.net.tomatoegg.ai.service;

import cn.net.tomatoegg.ai.integration.QwenIntegration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.document.Document;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.stream.Collectors;
import java.util.UUID;

/**
 * AI Service
 *
 * @author 苏三
 * @date 2025/2/13 09:48
 */
@Slf4j
@Service
public class AiService {

    private final ChatClient chatClient;
    private final QwenIntegration qwenIntegration;
    private final VectorStore vectorStore;
    private final ConversationService conversationService;
    private final RerankService rerankService;
    private final KnowledgeBaseService knowledgeBaseService;

    private static final String RAG_SYSTEM_PROMPT = "你是一个智能助手，请根据用户提供的上下文信息回答问题。如果不确定或上下文不包含相关信息，请直接回答不知道，不要编造内容。";

    private static final String NORMAL_SYSTEM_PROMPT = "你是一名专业的智能助手，请基于上下文对话历史，准确、简洁地回答用户问题。如果你不确定答案，请直接说明不知道。";

    public AiService(ChatClient.Builder chatClientBuilder,
                     QwenIntegration qwenIntegration,
                     ChatMemory chatMemory,
                     VectorStore vectorStore,
                     ConversationService conversationService,
                     RerankService rerankService,
                     KnowledgeBaseService knowledgeBaseService) {
        this.qwenIntegration = qwenIntegration;
        this.vectorStore = vectorStore;
        this.conversationService = conversationService;
        this.rerankService = rerankService;
        this.knowledgeBaseService = knowledgeBaseService;
        this.chatClient = chatClientBuilder.build();
    }


    /**
     * RAG 对话接口 - 带知识库增强的 AI 对话
     *
     * 【执行流程】
     * 1. 保存用户问题到数据库
     * 2. 向量检索：将问题转换为向量，在知识库中查找相似文档
     * 3. Rerank 重排序：对检索结果进行重排序，提高相关性
     * 4. 构建提示词：系统提示 + 历史对话 + 上下文信息 + 用户问题
     * 5. 调用千问 API（流式响应）
     * 6. 保存完整回复到数据库
     *
     * @param question       用户问题
     * @param conversationId 对话 ID（可选）
     * @param kbId           知识库 ID（可选）
     * @param model          模型名称
     * @return 流式响应 Flux<String>
     */
    public Flux<String> chatByRag(String question, UUID conversationId, UUID kbId, String model) {
        log.info("收到 RAG 对话请求，问题：{}, kbId: {}, model: {}", question, kbId, model);

        if (conversationId != null) {
            conversationService.addUserMessage(conversationId, question);
        }

        // 向量检索（支持按知识库过滤）
        List<Document> documents = knowledgeBaseService.searchFromKnowledgeBase(question, kbId, 5);

        // ========== 步骤 4: 判断检索结果 ==========
        // 如果没有找到相关文档，直接返回默认回复
        if (documents.isEmpty()) {
            log.info("未检索到相关文档片段，直接回答不知道");
            String fallback = "抱歉，知识库中没有找到相关信息。";
            if (conversationId != null) {
                conversationService.addAssistantMessage(conversationId, fallback);
            }
            return Flux.just(fallback);
        }

        // ========== 步骤 4.5: Rerank 重排序（新增）==========
        // 使用 Rerank 模型对检索结果进行重排序，提高相关性
        // 工作原理：
        // 1. 将查询问题和检索到的文档一起发送给 Rerank 模型
        // 2. Rerank 模型为每个文档计算相关性分数
        // 3. 按分数降序排序，过滤低分文档
        // 4. 返回 Top-N 最相关的文档
        //
        // 优势：
        // - 向量检索只考虑语义相似度，可能忽略关键词匹配
        // - Rerank 模型可以更精确地判断文档与问题的相关性
        // - 通常能提升 10-30% 的检索准确率
        if (rerankService.isEnabled()) {
            log.info("开始 Rerank 重排序...");
            documents = rerankService.rerank(question, documents);
            
            // Rerank 后如果没有文档，返回默认回复
            if (documents.isEmpty()) {
                log.info("Rerank 后没有符合条件的文档，返回默认回复");
                String fallback = "抱歉，知识库中没有找到与您的问题高度相关的信息。";
                if (conversationId != null) {
                    conversationService.addAssistantMessage(conversationId, fallback);
                }
                return Flux.just(fallback);
            }
        }

        // ========== 步骤 5: 打印检索到的文档信息（用于调试）==========
        // 遍历所有检索到的文档片段，记录到日志中
        // 方便排查问题，查看检索到了什么内容
        log.info("检索到 {} 个相关文档片段:", documents.size());
        for (int i = 0; i < documents.size(); i++) {
            Document doc = documents.get(i);
            // 从元数据中获取文件名，如果没有则显示"unknown"
            String filename = (String) doc.getMetadata().getOrDefault("filename", "unknown");
            // 只显示前 100 个字符，避免日志过长
            String contentSnippet = doc.getContent().length() > 100 ?
                    doc.getContent().substring(0, 100) + "..." : doc.getContent();
            log.info("片段 {}: filename={}, content={}", i + 1, filename, contentSnippet);
        }

        // ========== 步骤 6: 获取历史对话记录 ==========
        // 获取最近 20 条对话历史，用于保持对话的上下文连贯性
        // 格式化为："用户：问题 1\n助手：回答 1\n用户：问题 2\n助手：回答 2"
        String history = "";
        if (conversationId != null) {
            history = conversationService.listRecentMessages(conversationId, 20).stream()
                    .map(m -> (m.role().equalsIgnoreCase("USER") ? "用户" : "助手") + ": " + m.content())
                    .collect(Collectors.joining("\n"));
        }

        // ========== 步骤 7: 拼接检索到的文档内容 ==========
        // 将所有检索到的文档片段内容用两个换行符连接起来
        // 形成完整的上下文信息，供 AI 模型参考
        String context = documents.stream()
                .map(Document::getContent)
                .collect(Collectors.joining("\n\n"));

        // ========== 步骤 8: 构建完整的提示词 ==========
        // 最终发送给千问 API 的提示词包含四部分：
        // 1. 系统提示：定义 AI 的角色和行为规范
        // 2. 历史对话：之前的对话记录
        // 3. 上下文信息：从知识库检索到的相关文档
        // 4. 用户问题：当前要回答的问题
        String userPrompt = "【历史对话】:\n" + history + "\n\n【上下文信息】:\n" + context + "\n\n【用户问题】:\n" + question;
        log.info("构造的 RAG 用户提示词:\n{}", userPrompt);

        // ========== 步骤 9: 调用千问 API（流式响应）==========
        // 使用 StringBuilder 累积完整的 AI 回复
        // 原因：流式响应是逐字返回的，需要累积后才能保存完整回复到数据库
        StringBuilder responseBuilder = new StringBuilder();
        
        // 组合系统提示和用户提示词
        String composed = RAG_SYSTEM_PROMPT + "\n\n" + userPrompt;
        
        // 调用千问集成服务，获取流式响应
        // qwenIntegration.chatByStream() 返回 Flux<ChatResponse>
        Flux<String> fluxResult = qwenIntegration.chatByStream(composed)
                // 从 ChatResponse 中提取文本内容
                // ChatResponse.getResult().getOutput().getText() 获取实际文本
                .flatMap(r -> Flux.just(extractText(r)))
                // 每收到一个文本片段，就追加到 responseBuilder 中
                .doOnNext(responseBuilder::append)
                // 当流式响应完成时，执行后续操作
                .doOnComplete(() -> {
                    // 记录完整回复内容的日志
                    log.info("RAG 对话响应完成，完整回复内容：{}", responseBuilder.toString());
                    // 将 AI 的完整回复保存到数据库，角色为"ASSISTANT"
                    if (conversationId != null) {
                        conversationService.addAssistantMessage(conversationId, responseBuilder.toString());
                    }
                });
        
        // ========== 步骤 10: 返回流式响应给前端 ==========
        // 前端会逐字接收文本，实现打字机效果
        return fluxResult;
    }




    public Flux<String> chatByStream(String question, UUID conversationId, String model) {
        log.info("收到普通 AI 对话请求，问题：{}, model: {}", question, model);
        StringBuilder fullReply = new StringBuilder();
        if (conversationId != null) {
            conversationService.addUserMessage(conversationId, question);
        }

        String history = "";
        if (conversationId != null) {
            history = conversationService.listRecentMessages(conversationId, 20).stream()
                    .map(m -> (m.role().equalsIgnoreCase("USER") ? "用户" : "助手") + ": " + m.content())
                    .collect(Collectors.joining("\n"));
        }
        String composed = NORMAL_SYSTEM_PROMPT + "\n\n【历史对话】\n" + history + "\n\n【用户问题】\n" + question;

        Flux<String> fluxResult = qwenIntegration.chatByStream(composed)
                .flatMap(r -> Flux.just(extractText(r)))
                .doOnNext(fullReply::append)
                .doOnComplete(() -> {
                    log.info("普通 AI 对话响应完成，完整回复内容：{}", fullReply);
                    if (conversationId != null) {
                        conversationService.addAssistantMessage(conversationId, fullReply.toString());
                    }
                });

        return fluxResult;
    }

    private String extractText(ChatResponse response) {
        if (response == null || response.getResult() == null || response.getResult().getOutput() == null
                || response.getResult().getOutput().getText() == null) {
            return "";
        }
        String text = response.getResult().getOutput().getText();
        log.debug("extractText: {}", text);
        return text;
    }
}
