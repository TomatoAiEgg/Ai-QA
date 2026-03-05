package cn.net.susan.ai.service;

import cn.net.susan.ai.integration.OllamaIntegration;
import cn.net.susan.ai.integration.QwenIntegration;
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
    private final OllamaIntegration ollamaIntegration;
    private final QwenIntegration qwenIntegration;
    private final VectorStore vectorStore;
    private final ConversationService conversationService;

    // RAG 提示词模板
    private static final String RAG_SYSTEM_PROMPT = """
            你是一个智能助手，请根据用户提供的上下文信息回答问题。
            如果不确定或上下文不包含相关信息，请直接回答不知道，不要编造内容。
            """;

    private static final String RAG_USER_PROMPT_TEMPLATE = """
            【历史对话】：
            %s
            
            【上下文信息】：
            %s
            
            【用户问题】：
            %s
            """;
    private static final String NORMAL_SYSTEM_PROMPT = """
            你是一名专业的智能助手，请基于上下文对话历史，准确、简洁地回答用户问题。
            如果你不确定答案，请直接说明不知道。
            """;

    public AiService(ChatClient.Builder chatClientBuilder,
                     OllamaIntegration ollamaIntegration,
                     QwenIntegration qwenIntegration,
                     ChatMemory chatMemory,
                     VectorStore vectorStore,
                     ConversationService conversationService) {
        this.ollamaIntegration = ollamaIntegration;
        this.qwenIntegration = qwenIntegration;
        this.vectorStore = vectorStore;
        this.conversationService = conversationService;
        this.chatClient = chatClientBuilder
                .build();
    }

    /**
     * RAG 对话接口
     *
     * @param question 提示词
     * @return 流式返回的字符串
     */
    public Flux<String> chatByRag(String question) {
        return chatByRag(question, null);
    }

    public Flux<String> chatByRag(String question, UUID conversationId) {
        return chatByRag(question, conversationId, "qwen");
    }

    public Flux<String> chatByRag(String question, UUID conversationId, String model) {
        // 1. 记录用户问题
        log.info("收到 RAG 对话请求，问题: {}, model: {}", question, model);

        if (conversationId != null) {
            conversationService.addUserMessage(conversationId, question);
        }

        // 2. 检索向量数据库，增加相似度阈值
        List<Document> documents = vectorStore.similaritySearch(
                SearchRequest.query(question).withTopK(5).withSimilarityThreshold(0.6));
        
        // 3. 记录检索到的文档信息
        if (documents.isEmpty()) {
            log.info("未检索到相关文档片段，直接回答不知道");
            String fallback = "抱歉，知识库中没有找到相关信息。";
            if (conversationId != null) {
                conversationService.addAssistantMessage(conversationId, fallback);
            }
            return Flux.just(fallback);
        }

        log.info("检索到 {} 个相关文档片段:", documents.size());
        for (int i = 0; i < documents.size(); i++) {
            Document doc = documents.get(i);
            String filename = (String) doc.getMetadata().getOrDefault("filename", "unknown");
            String contentSnippet = doc.getContent().length() > 100 ? 
                    doc.getContent().substring(0, 100) + "..." : doc.getContent();
            log.info("片段 {}: filename={}, content={}", i + 1, filename, contentSnippet);
        }

        String history = "";
        if (conversationId != null) {
            history = conversationService.listRecentMessages(conversationId, 20).stream()
                    .map(m -> (m.role().equalsIgnoreCase("USER") ? "用户" : "助手") + "：" + m.content())
                    .collect(Collectors.joining("\n"));
        }

        // 4. 构建上下文
        String context = documents.stream()
                .map(Document::getContent)
                .collect(Collectors.joining("\n\n"));

        // 5. 构建用户提示词
        String userPrompt = String.format(RAG_USER_PROMPT_TEMPLATE, history, context, question);
        log.info("构造的 RAG 用户提示词:\n{}", userPrompt);

        // 6. 调用大模型
        StringBuilder responseBuilder = new StringBuilder();
        String composed = RAG_SYSTEM_PROMPT + "\n\n" + userPrompt;
        return streamByModel(model, composed)
                .doOnNext(responseFragment -> responseBuilder.append(responseFragment))
                .doOnComplete(() -> {
                    log.info("RAG 对话响应完成，完整回复内容: {}", responseBuilder.toString());
                    if (conversationId != null) {
                        conversationService.addAssistantMessage(conversationId, responseBuilder.toString());
                    }
                });
    }


    /**
     * 对话接口
     *
     * @param question 提示词
     * @return 字符串
     */
    public String chatByOllama(String question) {
        return ollamaIntegration.chat(question);
    }

    /**
     * 对话接口，流式返回字符串
     *
     * @param question 提示词
     * @return 流式返回的字符串
     */
    public Flux<String> chatByStream(String question) {
        return chatByStream(question, null);
    }

    public Flux<String> chatByStream(String question, UUID conversationId) {
        return chatByStream(question, conversationId, "qwen");
    }

    public Flux<String> chatByStream(String question, UUID conversationId, String model) {
        log.info("收到普通 AI 对话请求，问题: {}, model: {}", question, model);
        StringBuilder fullReply = new StringBuilder();
        if (conversationId != null) {
            conversationService.addUserMessage(conversationId, question);
        }

        String history = "";
        if (conversationId != null) {
            history = conversationService.listRecentMessages(conversationId, 20).stream()
                    .map(m -> (m.role().equalsIgnoreCase("USER") ? "用户" : "助手") + "：" + m.content())
                    .collect(Collectors.joining("\n"));
        }
        String composed = NORMAL_SYSTEM_PROMPT + "\n\n" +
                "【历史对话】\n" + history + "\n\n" +
                "【用户问题】\n" + question;

        Flux<String> fluxResult = streamByModel(model, composed)
                .doOnNext(fullReply::append)
                .doOnComplete(() -> {
                    //监听流式响应完成，完整回复存入消息记录
                    log.info("普通 AI 对话响应完成，完整回复内容: {}", fullReply);
                    if (conversationId != null) {
                        conversationService.addAssistantMessage(conversationId, fullReply.toString());
                    }
                });

        return fluxResult;
    }

    private Flux<String> streamByModel(String model, String promptText) {
        String requested = (model == null || model.isBlank()) ? "qwen" : model.toLowerCase();
        if ("qwen".equals(requested)) {
            if (qwenIntegration.isEnabled()) {
                log.info("请求大模型：provider=qwen, model={}", qwenIntegration.getConfiguredModelName());
                return qwenIntegration.chatByStream(promptText).flatMap(r -> Flux.just(extractText(r)));
            }
            log.warn("请求大模型：provider=qwen 未启用，降级到 provider=ollama, model={}", ollamaIntegration.getConfiguredModelName());
            return ollamaIntegration.chatByStream(promptText).flatMap(r -> Flux.just(extractText(r)));
        }

        if ("ollama".equals(requested)) {
            log.info("请求大模型：provider=ollama, model={}", ollamaIntegration.getConfiguredModelName());
            return ollamaIntegration.chatByStream(promptText).flatMap(r -> Flux.just(extractText(r)));
        }

        if (qwenIntegration.isEnabled()) {
            log.info("请求大模型：provider=qwen, model={}", qwenIntegration.getConfiguredModelName());
            return qwenIntegration.chatByStream(promptText).flatMap(r -> Flux.just(extractText(r)));
        }
        log.info("请求大模型：provider=ollama, model={}", ollamaIntegration.getConfiguredModelName());
        return ollamaIntegration.chatByStream(promptText).flatMap(r -> Flux.just(extractText(r)));
    }

    private String extractText(ChatResponse response) {
        if (response == null || response.getResult() == null || response.getResult().getOutput() == null
                || response.getResult().getOutput().getText() == null) {
            return "";
        }
        return response.getResult().getOutput().getText();
    }
}
