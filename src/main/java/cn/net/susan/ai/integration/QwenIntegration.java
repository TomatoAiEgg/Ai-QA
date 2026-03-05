package cn.net.susan.ai.integration;

import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

@Component
public class QwenIntegration {

    private final ObjectProvider<OpenAiChatModel> openAiChatModelProvider;
    private final String configuredModel;

    public QwenIntegration(ObjectProvider<OpenAiChatModel> openAiChatModelProvider,
                           @Value("${spring.ai.openai.chat.options.model:}") String configuredModel) {
        this.openAiChatModelProvider = openAiChatModelProvider;
        this.configuredModel = configuredModel;
    }

    public boolean isEnabled() {
        return openAiChatModelProvider.getIfAvailable() != null;
    }

    public Flux<ChatResponse> chatByStream(String promptText) {
        OpenAiChatModel chatModel = openAiChatModelProvider.getIfAvailable();
        if (chatModel == null) {
            return Flux.error(new IllegalStateException("千问模型未配置：请设置 spring.ai.openai.api-key"));
        }
        return chatModel.stream(new Prompt(promptText));
    }

    public String getConfiguredModelName() {
        if (configuredModel == null || configuredModel.isBlank()) {
            return "qwen";
        }
        return configuredModel;
    }
}
