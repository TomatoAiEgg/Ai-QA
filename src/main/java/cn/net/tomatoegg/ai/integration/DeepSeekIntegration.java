package cn.net.tomatoegg.ai.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * DeepSeek AI
 *
 * @author 苏三
 * @date 2025/2/27 09:58
 */
@Slf4j
//@Component
public class DeepSeekIntegration {

    @Value("${spring.ai.openai.api-key}")
    private String apiKey;
    @Value("${spring.ai.openai.base-url}")
    private String apiUrl;

    private static final String DEFAULT_USER_ID = "888";

    // 用于保存每个用户的对话历史
    private final Map<String, List<Map<String, String>>> sessionHistory = new ConcurrentHashMap<>();

    private final ExecutorService executorService = Executors.newCachedThreadPool();
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 调用DeekSeek API 实现对话
     *
     * @param question 问题
     * @return 流式回答
     */
    public SseEmitter chat(String question) {
        SseEmitter emitter = new SseEmitter(-1L);
        executorService.execute(() -> {
            try {
                log.info("流式回答开始, 问题: {}", question);

                // 获取当前用户的对话历史
                List<Map<String, String>> messages = sessionHistory.getOrDefault(DEFAULT_USER_ID, new ArrayList<>());

                // 添加用户的新问题到对话历史
                Map<String, String> userMessage = new HashMap<>();
                userMessage.put("role", "user");
                userMessage.put("content", question);

                Map<String, String> systemMessage = new HashMap<>();
                systemMessage.put("role", "system");
                systemMessage.put("content", "苏三AI问答助手");
                messages.add(userMessage);
                messages.add(systemMessage);

                // 调用 Deepseek API
                try (CloseableHttpClient client = HttpClients.createDefault()) {
                    HttpPost request = new HttpPost(apiUrl);
                    request.setHeader("Content-Type", "application/json");
                    request.setHeader("Authorization", "Bearer " + apiKey);

                    Map<String, Object> requestMap = new HashMap<>();
                    requestMap.put("model", "deepseek-chat");
//                    requestMap.put("model", "deepseek-reasoner");
                    requestMap.put("messages", messages); // 包含对话历史
                    requestMap.put("stream", true);

                    String requestBody = objectMapper.writeValueAsString(requestMap);
                    request.setEntity(new StringEntity(requestBody, StandardCharsets.UTF_8));

                    try (CloseableHttpResponse response = client.execute(request);
                         BufferedReader reader = new BufferedReader(
                                 new InputStreamReader(response.getEntity().getContent(), StandardCharsets.UTF_8))) {
                        StringBuilder aiResponse = new StringBuilder();
                        String line;
                        while ((line = reader.readLine()) != null) {
                            if (line.startsWith("data: ")) {
                                log.info("DeepSeek回答：{}", line);
                                String jsonData = line.substring(6);
                                if ("[DONE]".equals(jsonData)) {
                                    break;
                                }
                                JsonNode node = objectMapper.readTree(jsonData);
                                String content = node.path("choices")
                                        .path(0)
                                        .path("delta")
                                        .path("content")
                                        .asText("");
                                if (!content.isEmpty()) {
                                    emitter.send(content);
                                    aiResponse.append(content); // 收集 AI 的回复
                                }
                            }
                        }

                        // 将 AI 的回复添加到对话历史
                        Map<String, String> aiMessage = new HashMap<>();
                        aiMessage.put("role", "assistant");
                        aiMessage.put("content", aiResponse.toString());
                        messages.add(aiMessage);

                        // 更新会话状态
                        sessionHistory.put(DEFAULT_USER_ID, messages);

                        log.info("流式回答结束, 问题: {}", question);
                        emitter.complete();
                    }
                } catch (Exception e) {
                    log.error("处理 DeepSeek 请求时发生错误", e);
                    emitter.completeWithError(e);
                }
            } catch (Exception e) {
                log.error("处理 DeepSeek 请求时发生错误", e);
                emitter.completeWithError(e);
            }
        });
        return emitter;
    }

}