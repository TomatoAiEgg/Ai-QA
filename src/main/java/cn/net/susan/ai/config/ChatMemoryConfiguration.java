package cn.net.susan.ai.config;

import org.springframework.ai.chat.memory.InMemoryChatMemory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

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
}