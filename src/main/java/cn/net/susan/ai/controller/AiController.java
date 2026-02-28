package cn.net.susan.ai.controller;

import cn.net.susan.ai.service.AiService;
import cn.net.susan.ai.service.KnowledgeBaseService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.ModelAndView;
import reactor.core.publisher.Flux;

/**
 * @author 苏三
 * @date 2025/2/12 17:28
 */
@RestController
public class AiController {

    private final AiService aiService;
    private final KnowledgeBaseService knowledgeBaseService;

    public AiController(AiService aiService, KnowledgeBaseService knowledgeBaseService) {
        this.aiService = aiService;
        this.knowledgeBaseService = knowledgeBaseService;
    }

    /**
     * 访问苏三AI问答助手首页
     *
     * @param modelAndView
     * @return
     */
    @GetMapping("/")
    public ModelAndView chat(ModelAndView modelAndView) {
        modelAndView.setViewName("chat");
        return modelAndView;
    }

    /**
     * 访问RAG知识库管理页面
     *
     * @param modelAndView
     * @return
     */
    @GetMapping("/rag")
    public ModelAndView rag(ModelAndView modelAndView) {
        modelAndView.setViewName("rag");
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
    @PostMapping(value = "/ai/chatByOllama", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> chatByOllama(@RequestBody String question) {
        return aiService.chatByStream(question);
    }

    /**
     * RAG 对话接口
     *
     * @param question 问题
     * @return 流式回复
     */
    @PostMapping(value = "/ai/chatByRag", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> chatByRag(@RequestBody String question) {
        return aiService.chatByRag(question);
    }

    /**
     * 上传文档到知识库
     *
     * @param file 文件
     * @return 上传结果
     */
    @PostMapping("/ai/upload")
    public String upload(@RequestParam("file") MultipartFile file) {
        return knowledgeBaseService.uploadDocument(file);
    }

    @GetMapping("/ai/documents")
    public Object getDocuments() {
        return knowledgeBaseService.getDocumentList();
    }
}