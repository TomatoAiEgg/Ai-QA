package cn.net.susan.ai.service;

import cn.net.susan.ai.integration.OllamaIntegration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.PromptChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;

import java.io.IOException;

/**
 * AI Service
 *
 * @author 苏三
 * @date 2025/2/13 09:48
 */
@Slf4j
@Service
public class AiService {

    // private final ChatClient chatClient;
    private final OllamaIntegration ollamaIntegration;

    public AiService(ChatClient.Builder chatClientBuilder,
                     OllamaIntegration ollamaIntegration,
                     ChatMemory chatMemory) {
        this.ollamaIntegration = ollamaIntegration;
        /*  this.chatClient = chatClientBuilder*/
//                .defaultAdvisors(new PromptChatMemoryAdvisor(chatMemory))
//                .build();
    }


//    public Flux<String> chat(String question) {
//        //用户输入的信息提交给大模型，使用的是ChatClient与大模型交互。
//        Flux<String> content = this.chatClient.prompt()
//                .user(question)
//                .stream()
//                .content();
//        content.concatWith(Flux.just("[complete]"));
//        return content;
//    }

    /**
     * 对话接口
     *
     * @param question 提示词
     * @return 字符串
     */
    public String chatByOllama(String question) {
        return ollamaIntegration.chat(question);
    }

    /**
     * 对话接口，流式返回字符串
     *
     * @param question 提示词
     * @return 流式返回的字符串
     */
    public Flux<String> chatByStream(String question) {
        StringBuilder fullReply = new StringBuilder();
        Flux<String> fluxResult = ollamaIntegration.chatByStream(question)
                .flatMap(response -> {
                    String reply;
                    if (response.getResult() == null || response.getResult().getOutput() == null
                            || response.getResult().getOutput().getText() == null) {
                        reply = "";
                    } else {
                        reply = response.getResult().getOutput().getText();
                    }

                    //拼接回复内容
                    fullReply.append(reply);

                    return Flux.just(reply);
                })
                .doOnComplete(() -> {
                    //监听流式响应完成，完整回复存入消息记录
                    System.out.println(fullReply);
                    //addAssistantMessage(String.valueOf(fullReply));
                });

        return fluxResult;
    }
}