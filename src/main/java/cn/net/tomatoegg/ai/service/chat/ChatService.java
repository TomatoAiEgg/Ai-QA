package cn.net.tomatoegg.ai.service.chat;

import cn.net.tomatoegg.ai.integration.QwenIntegration;
import cn.net.tomatoegg.ai.service.conversation.ConversationService;
import cn.net.tomatoegg.ai.service.knowledgebase.KnowledgeBaseSearchService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
public class ChatService {

    private static final String RAG_SYSTEM_PROMPT = "你是一个智能助手，请根据用户提供的上下文信息回答问题。如果不确定或上下文不包含相关信息，请直接回答不知道，不要编造内容。";
    private static final String NORMAL_SYSTEM_PROMPT = "你是一名专业的智能助手，请基于上下文对话历史，准确、简洁地回答用户问题。如果你不确定答案，请直接说明不知道。";

    private final ChatClient chatClient;
    private final QwenIntegration qwenIntegration;
    private final ConversationService conversationService;
    private final RerankService rerankService;
    private final KnowledgeBaseSearchService knowledgeBaseSearchService;

    public ChatService(ChatClient.Builder chatClientBuilder,
                       QwenIntegration qwenIntegration,
                       ChatMemory chatMemory,
                       ConversationService conversationService,
                       RerankService rerankService,
                       KnowledgeBaseSearchService knowledgeBaseSearchService) {
        this.qwenIntegration = qwenIntegration;
        this.conversationService = conversationService;
        this.rerankService = rerankService;
        this.knowledgeBaseSearchService = knowledgeBaseSearchService;
        this.chatClient = chatClientBuilder.build();
    }

    public Flux<String> chatByRag(String question, UUID userId, UUID conversationId, UUID kbId, String model) {
        log.info("收到 RAG 对话请求，问题: {}, kbId: {}, model: {}", question, kbId, model);

        if (conversationId != null) {
            conversationService.addUserMessage(userId, conversationId, question);
        }

        List<Document> documents = knowledgeBaseSearchService.searchFromKnowledgeBase(question, kbId, 5, userId);
        if (documents.isEmpty()) {
            String fallback = "抱歉，知识库中没有找到相关信息。";
            if (conversationId != null) {
                conversationService.addAssistantMessage(userId, conversationId, fallback);
            }
            return Flux.just(fallback);
        }

        if (rerankService.isEnabled()) {
            documents = rerankService.rerank(question, documents);
            if (documents.isEmpty()) {
                String fallback = "抱歉，知识库中没有找到与你的问题高度相关的信息。";
                if (conversationId != null) {
                    conversationService.addAssistantMessage(userId, conversationId, fallback);
                }
                return Flux.just(fallback);
            }
        }

        String history = buildHistory(userId, conversationId);
        String context = documents.stream().map(Document::getContent).collect(Collectors.joining("\n\n"));
        String userPrompt = "【历史对话】\n" + history + "\n\n【上下文信息】\n" + context + "\n\n【用户问题】\n" + question;
        String composed = RAG_SYSTEM_PROMPT + "\n\n" + userPrompt;

        StringBuilder responseBuilder = new StringBuilder();
        return qwenIntegration.chatByStream(composed, model)
                .flatMap(response -> Flux.just(extractText(response)))
                .doOnNext(responseBuilder::append)
                .doOnComplete(() -> {
                    if (conversationId != null) {
                        conversationService.addAssistantMessage(userId, conversationId, responseBuilder.toString());
                    }
                    log.info("RAG 对话完成: {}", responseBuilder);
                });
    }

    public Flux<String> chatByStream(String question, UUID userId, UUID conversationId, String model) {
        log.info("收到普通 AI 对话请求，问题: {}, model: {}", question, model);
        if (conversationId != null) {
            conversationService.addUserMessage(userId, conversationId, question);
        }

        String history = buildHistory(userId, conversationId);
        String composed = NORMAL_SYSTEM_PROMPT + "\n\n【历史对话】\n" + history + "\n\n【用户问题】\n" + question;

        StringBuilder fullReply = new StringBuilder();
        return qwenIntegration.chatByStream(composed, model)
                .flatMap(response -> Flux.just(extractText(response)))
                .doOnNext(fullReply::append)
                .doOnComplete(() -> {
                    if (conversationId != null) {
                        conversationService.addAssistantMessage(userId, conversationId, fullReply.toString());
                    }
                    log.info("普通对话完成: {}", fullReply);
                });
    }

    private String buildHistory(UUID userId, UUID conversationId) {
        if (conversationId == null) {
            return "";
        }
        return conversationService.listRecentMessages(userId, conversationId, 20).stream()
                .map(message -> (message.getRole().equalsIgnoreCase("USER") ? "用户" : "助手") + ": " + message.getContent())
                .collect(Collectors.joining("\n"));
    }

    private String extractText(ChatResponse response) {
        if (response == null || response.getResult() == null || response.getResult().getOutput() == null) {
            return "";
        }
        String text = response.getResult().getOutput().getText();
        return text == null ? "" : text;
    }
}
