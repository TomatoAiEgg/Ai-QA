package cn.net.tomatoegg.ai.service;

import cn.net.tomatoegg.ai.config.ModelRouterProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

@Slf4j
@Service
public class ModelRouterService {

    private static final String CHAT_TYPE = "chat";
    private static final String EMBEDDING_TYPE = "embedding";

    private final ModelRouterProperties properties;
    private final RedisTemplate<String, Object> redisTemplate;

    public ModelRouterService(ModelRouterProperties properties, RedisTemplate<String, Object> redisTemplate) {
        this.properties = properties;
        this.redisTemplate = redisTemplate;
    }

    public List<String> getChatCandidates(String requestedModel) {
        return buildCandidates(CHAT_TYPE, requestedModel, properties.getChat());
    }

    public List<String> getEmbeddingCandidates() {
        return buildCandidates(EMBEDDING_TYPE, null, properties.getEmbedding());
    }

    public int getChatTransientRetries() {
        return Math.max(properties.getChat().getTransientRetries(), 0);
    }

    public int getEmbeddingTransientRetries() {
        return Math.max(properties.getEmbedding().getTransientRetries(), 0);
    }

    public void markChatModelExhausted(String model, Throwable throwable) {
        markExhausted(CHAT_TYPE, model, properties.getChat().getExhaustedTtl(), throwable);
    }

    public void markEmbeddingModelExhausted(String model, Throwable throwable) {
        markExhausted(EMBEDDING_TYPE, model, properties.getEmbedding().getExhaustedTtl(), throwable);
    }

    public boolean isQuotaExhausted(Throwable throwable) {
        String message = flattenMessage(throwable);
        return message.contains("allocationquota.freetieronly")
                || message.contains("freetieronly")
                || message.contains("free tier")
                || message.contains("免费额度")
                || message.contains("quota exceeded");
    }

    public boolean isTransientFailure(Throwable throwable) {
        String message = flattenMessage(throwable);
        return message.contains("connection reset")
                || message.contains("connection prematurely closed")
                || message.contains("connection refused")
                || message.contains("read timed out")
                || message.contains("timeout")
                || message.contains("i/o error")
                || message.contains("broken pipe")
                || message.contains("reset by peer");
    }

    private List<String> buildCandidates(String type, String requestedModel, ModelRouterProperties.RouteProperties routeProperties) {
        Set<String> ordered = new LinkedHashSet<>();
        String normalizedRequestedModel = normalizeRequestedModel(requestedModel);
        if (normalizedRequestedModel != null) {
            ordered.add(normalizedRequestedModel);
        }
        if (routeProperties.getDefaultModel() != null && !routeProperties.getDefaultModel().isBlank()) {
            ordered.add(routeProperties.getDefaultModel().trim());
        }
        if (routeProperties.getFallbackModels() != null) {
            routeProperties.getFallbackModels().stream()
                    .filter(Objects::nonNull)
                    .map(String::trim)
                    .filter(model -> !model.isBlank())
                    .forEach(ordered::add);
        }

        List<String> available = new ArrayList<>();
        for (String model : ordered) {
            if (!isMarkedExhausted(type, model)) {
                available.add(model);
            }
        }
        if (available.isEmpty()) {
            available.addAll(ordered);
        }
        return available;
    }

    private String normalizeRequestedModel(String requestedModel) {
        if (requestedModel == null) {
            return null;
        }
        String trimmed = requestedModel.trim();
        if (trimmed.isBlank()) {
            return null;
        }
        if ("qwen".equalsIgnoreCase(trimmed) || "default".equalsIgnoreCase(trimmed) || "auto".equalsIgnoreCase(trimmed)) {
            return null;
        }
        return trimmed;
    }

    private boolean isMarkedExhausted(String type, String model) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(buildExhaustedKey(type, model)));
    }

    private void markExhausted(String type, String model, Duration ttl, Throwable throwable) {
        if (model == null || model.isBlank()) {
            return;
        }
        redisTemplate.opsForValue().set(buildExhaustedKey(type, model), Boolean.TRUE, ttl);
        log.warn("模型已标记为短期不可用, type={}, model={}, ttl={}, reason={}", type, model, ttl, throwable == null ? "" : throwable.getMessage());
    }

    private String buildExhaustedKey(String type, String model) {
        return "model:router:exhausted:" + type + ":" + model;
    }

    private String flattenMessage(Throwable throwable) {
        StringBuilder builder = new StringBuilder();
        Throwable cursor = throwable;
        while (cursor != null) {
            if (cursor.getMessage() != null) {
                builder.append(cursor.getMessage()).append('\n');
            }
            cursor = cursor.getCause();
        }
        return builder.toString().toLowerCase(Locale.ROOT);
    }
}
