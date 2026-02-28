package cn.net.susan.ai.service;

import cn.net.susan.ai.integration.OllamaIntegration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.stream.Collectors;

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
    private final VectorStore vectorStore;

    // RAG 提示词模板
    private static final String RAG_SYSTEM_PROMPT = """
            你是一个智能助手，请根据用户提供的上下文信息回答问题。
            如果不确定或上下文不包含相关信息，请直接回答不知道，不要编造内容。
            """;

    private static final String RAG_USER_PROMPT_TEMPLATE = """
            【上下文信息】：
            %s
            
            【用户问题】：
            %s
            """;

    public AiService(ChatClient.Builder chatClientBuilder,
                     OllamaIntegration ollamaIntegration,
                     ChatMemory chatMemory,
                     VectorStore vectorStore) {
        this.ollamaIntegration = ollamaIntegration;
        this.vectorStore = vectorStore;
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
        // 1. 记录用户问题
        log.info("收到 RAG 对话请求，问题: {}", question);

        // 2. 检索向量数据库，增加相似度阈值
        List<Document> documents = vectorStore.similaritySearch(
                SearchRequest.query(question).withTopK(5).withSimilarityThreshold(0.6));
        
        // 3. 记录检索到的文档信息
        if (documents.isEmpty()) {
            log.info("未检索到相关文档片段，直接回答不知道");
            return Flux.just("抱歉，知识库中没有找到相关信息。");
        }

        log.info("检索到 {} 个相关文档片段:", documents.size());
        for (int i = 0; i < documents.size(); i++) {
            Document doc = documents.get(i);
            String filename = (String) doc.getMetadata().getOrDefault("filename", "unknown");
            String contentSnippet = doc.getContent().length() > 100 ? 
                    doc.getContent().substring(0, 100) + "..." : doc.getContent();
            log.info("片段 {}: filename={}, content={}", i + 1, filename, contentSnippet);
        }

        // 4. 构建上下文
        String context = documents.stream()
                .map(Document::getContent)
                .collect(Collectors.joining("\n\n"));

        // 5. 构建用户提示词
        String userPrompt = String.format(RAG_USER_PROMPT_TEMPLATE, context, question);
        log.info("构造的 RAG 用户提示词:\n{}", userPrompt);

        // 6. 调用大模型
        StringBuilder responseBuilder = new StringBuilder();
        return chatClient.prompt()
                .system(RAG_SYSTEM_PROMPT)
                .user(userPrompt)
                .stream()
                .content()
                .doOnNext(responseFragment -> {
                    responseBuilder.append(responseFragment);
                })
                .doOnComplete(() -> {
                    log.info("RAG 对话响应完成，完整回复内容: {}", responseBuilder.toString());
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
        log.info("收到普通 AI 对话请求，问题: {}", question);
        StringBuilder fullReply = new StringBuilder();
        Flux<String> fluxResult = ollamaIntegration.chatByStream(question)
                .flatMap(response -> {
                    String reply;
                    if (response.getResult() == null || response.getResult().getOutput() == null
                            || response.getResult().getOutput().getText() == null) {
                        reply = "";
                    } else {
                        reply = response.getResult().getOutput().getText();
                    }

                    //拼接回复内容
                    fullReply.append(reply);

                    return Flux.just(reply);
                })
                .doOnComplete(() -> {
                    //监听流式响应完成，完整回复存入消息记录
                    log.info("普通 AI 对话响应完成，完整回复内容: {}", fullReply);
                });

        return fluxResult;
    }
}