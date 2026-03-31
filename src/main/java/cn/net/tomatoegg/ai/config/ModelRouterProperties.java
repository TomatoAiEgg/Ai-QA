package cn.net.tomatoegg.ai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "ai.service.model-router")
public class ModelRouterProperties {

    private final RouteProperties chat = new RouteProperties();
    private final RouteProperties embedding = new RouteProperties();

    public RouteProperties getChat() {
        return chat;
    }

    public RouteProperties getEmbedding() {
        return embedding;
    }

    public static class RouteProperties {

        private String defaultModel;
        private List<String> fallbackModels = new ArrayList<>();
        private Duration exhaustedTtl = Duration.ofHours(1);
        private int transientRetries = 1;

        public String getDefaultModel() {
            return defaultModel;
        }

        public void setDefaultModel(String defaultModel) {
            this.defaultModel = defaultModel;
        }

        public List<String> getFallbackModels() {
            return fallbackModels;
        }

        public void setFallbackModels(List<String> fallbackModels) {
            this.fallbackModels = fallbackModels;
        }

        public Duration getExhaustedTtl() {
            return exhaustedTtl;
        }

        public void setExhaustedTtl(Duration exhaustedTtl) {
            this.exhaustedTtl = exhaustedTtl;
        }

        public int getTransientRetries() {
            return transientRetries;
        }

        public void setTransientRetries(int transientRetries) {
            this.transientRetries = transientRetries;
        }
    }
}
