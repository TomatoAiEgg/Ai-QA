package cn.net.susan.ai.config;

import org.springframework.ai.chat.memory.InMemoryChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.ollama.OllamaEmbeddingModel;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
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
    public ChatModel primaryChatModel(ObjectProvider<OpenAiChatModel> openAiChatModelProvider,
                                      ObjectProvider<OllamaChatModel> ollamaChatModelProvider) {
        OpenAiChatModel openAi = openAiChatModelProvider.getIfAvailable();
        if (openAi != null) {
            return openAi;
        }
        OllamaChatModel ollama = ollamaChatModelProvider.getIfAvailable();
        if (ollama == null) {
            throw new IllegalStateException("未找到可用的 ChatModel，请检查 OpenAI 或 Ollama 配置");
        }
        return ollama;
    }

    @Bean
    @Primary
    public EmbeddingModel primaryEmbeddingModel(OllamaEmbeddingModel ollamaEmbeddingModel) {
        return ollamaEmbeddingModel;
    }
}
