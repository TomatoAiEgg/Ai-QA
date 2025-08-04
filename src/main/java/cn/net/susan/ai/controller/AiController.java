package cn.net.susan.ai.controller;

import cn.net.susan.ai.service.AiService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;

/**
 * @author 苏三
 * @date 2025/2/12 17:28
 */
@RequestMapping("/ai")
@RestController
public class AiController {

    private final AiService aiService;

    public AiController(AiService aiService) {
        this.aiService = aiService;
    }

    /**
     * 访问苏三AI问答助手首页
     *
     * @param modelAndView
     * @return
     */
    @GetMapping("/index")
    public ModelAndView chat(ModelAndView modelAndView) {
        modelAndView.setViewName("chat");
        return modelAndView;
    }

    /**
     * AI对话接口
     *
     * @param question 问题
     * @return 流式回复
     */
//    @PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
//    public Flux<String> chat(@RequestBody String question) {
//        return aiService.chat(question);
//    }


    /**
     * AI对话接口
     *
     * @param question 问题
     * @return 流式回复
     */
    @PostMapping(value = "/chatByOllama", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> chatByOllama(@RequestBody String question) {
        return aiService.chatByStream(question);
    }
}