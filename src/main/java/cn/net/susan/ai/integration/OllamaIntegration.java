package cn.net.susan.ai.integration;

import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

/**
 * Ollama 本地AI
 *
 * @author 苏三
 * @date 2025/3/6 19:36
 */
@Component
public class OllamaIntegration {

    private final OllamaChatModel ollamaChatModel;
    private final String configuredModel;

    public OllamaIntegration(OllamaChatModel ollamaChatModel,
                             @Value("${spring.ai.ollama.chat.model:}") String configuredModel) {
        this.ollamaChatModel = ollamaChatModel;
        this.configuredModel = configuredModel;
    }

    /**
     * 对话接口
     *
     * @param question 提示词
     * @return 字符串
     */
    public String chat(String question) {
        return ollamaChatModel.call(question);
    }

    /**
     * 对话接口，流式返回字符串
     *
     * @param question 提示词
     * @return 流式返回的字符串
     */
    public Flux<ChatResponse> chatByStream(String question) {
        Prompt prompt = new Prompt(question);
        return ollamaChatModel.stream(prompt);
    }

    public String getConfiguredModelName() {
        if (configuredModel == null || configuredModel.isBlank()) {
            return "ollama";
        }
        return configuredModel;
    }
}
