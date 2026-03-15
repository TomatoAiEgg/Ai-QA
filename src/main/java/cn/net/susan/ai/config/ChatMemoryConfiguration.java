package cn.net.susan.ai.config;

import org.springframework.ai.chat.memory.InMemoryChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.beans.factory.ObjectProvider;

/**
 * 内存对话配置
 *
 * @author 苏三
 * @date 2025/3/2 20:12
 */
@Configuration
public class ChatMemoryConfiguration {

    @Bean
    public InMemoryChatMemory inMemoryChatMemory() {
        return new InMemoryChatMemory();
    }

    @Bean
    @Primary
    public ChatModel primaryChatModel(ObjectProvider<OpenAiChatModel> openAiChatModelProvider) {
        OpenAiChatModel openAi = openAiChatModelProvider.getIfAvailable();
        if (openAi == null) {
            throw new IllegalStateException("未找到可用的 ChatModel，请检查 OpenAI 配置");
        }
        return openAi;
    }

    @Bean
    @Primary
    public EmbeddingModel primaryEmbeddingModel(ObjectProvider<OpenAiEmbeddingModel> openAiEmbeddingModelProvider) {
        OpenAiEmbeddingModel openAiEmbedding = openAiEmbeddingModelProvider.getIfAvailable();
        if (openAiEmbedding == null) {
            throw new IllegalStateException("未找到可用的 EmbeddingModel，请检查 OpenAI 配置");
        }
        return openAiEmbedding;
    }
}
