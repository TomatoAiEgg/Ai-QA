package cn.net.susan.ai.integration;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

/**
 * 智普AI
 *
 * @author 苏三
 * @date 2025/2/13 09:48
 */
@Component
public class ZhiPuIntegration {

    private final ChatClient chatClient;

    public ZhiPuIntegration(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
    }


    /**
     * 对话
     *
     * @param message 用户输入的消息
     * @return 大模型输出的信息
     */
    public String chat(String message) {
        //用户输入的信息提交给大模型，使用的是ChatClient与大模型交互。
        return this.chatClient.prompt()
                .user(message)
                .call()
                .content();
    }
}