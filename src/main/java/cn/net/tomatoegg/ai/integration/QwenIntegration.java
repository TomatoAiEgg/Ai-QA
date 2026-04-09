package cn.net.tomatoegg.ai.integration;

import cn.net.tomatoegg.ai.service.model.ModelRouterService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.List;

@Slf4j
@Component
public class QwenIntegration {

    private final ObjectProvider<OpenAiChatModel> openAiChatModelProvider;
    private final ModelRouterService modelRouterService;

    public QwenIntegration(ObjectProvider<OpenAiChatModel> openAiChatModelProvider,
                           ModelRouterService modelRouterService) {
        this.openAiChatModelProvider = openAiChatModelProvider;
        this.modelRouterService = modelRouterService;
    }

    public boolean isEnabled() {
        return openAiChatModelProvider.getIfAvailable() != null;
    }

    public Flux<ChatResponse> chatByStream(String promptText, String requestedModel) {
        OpenAiChatModel chatModel = openAiChatModelProvider.getIfAvailable();
        if (chatModel == null) {
            return Flux.error(new IllegalStateException("千问模型未配置：请设置 spring.ai.openai.api-key"));
        }
        List<String> candidates = modelRouterService.getChatCandidates(requestedModel);
        return streamWithFallback(chatModel, promptText, candidates, 0, 0);
    }

    public String getConfiguredModelName() {
        return modelRouterService.getDefaultChatModel();
    }

    private Flux<ChatResponse> streamWithFallback(OpenAiChatModel chatModel,
                                                  String promptText,
                                                  List<String> candidates,
                                                  int candidateIndex,
                                                  int retryAttempt) {
        if (candidateIndex >= candidates.size()) {
            return Flux.error(new IllegalStateException("当前没有可用的千问聊天模型，请稍后再试"));
        }

        String model = candidates.get(candidateIndex);
        OpenAiChatOptions options = OpenAiChatOptions.builder()
                .model(model)
                .build();

        log.info("开始调用聊天模型, model={}, candidateIndex={}, retryAttempt={}", model, candidateIndex, retryAttempt);
        return chatModel.stream(new Prompt(promptText, options))
                .onErrorResume(error -> {
                    if (modelRouterService.isQuotaExhausted(error)) {
                        modelRouterService.markChatModelExhausted(model, error);
                        log.warn("聊天模型免费额度或配额已耗尽，切换下一个模型, model={}", model);
                        return streamWithFallback(chatModel, promptText, candidates, candidateIndex + 1, 0);
                    }

                    if (modelRouterService.isTransientFailure(error)
                            && retryAttempt < modelRouterService.getChatTransientRetries()) {
                        log.warn("聊天模型调用瞬时失败，重试当前模型, model={}, retryAttempt={}", model, retryAttempt + 1);
                        return streamWithFallback(chatModel, promptText, candidates, candidateIndex, retryAttempt + 1);
                    }

                    if (modelRouterService.isTransientFailure(error) && candidateIndex + 1 < candidates.size()) {
                        log.warn("聊天模型调用失败，切换备用模型, currentModel={}, nextModel={}", model, candidates.get(candidateIndex + 1));
                        return streamWithFallback(chatModel, promptText, candidates, candidateIndex + 1, 0);
                    }

                    return Flux.error(error);
                });
    }
}
