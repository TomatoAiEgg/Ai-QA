package cn.net.susan.ai.controller;

import cn.net.susan.ai.service.AiService;
import cn.net.susan.ai.service.ConversationService;
import cn.net.susan.ai.service.KnowledgeBaseService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.ModelAndView;
import reactor.core.publisher.Flux;

import java.util.Map;
import java.util.UUID;

/**
 * @author 苏三
 * @date 2025/2/12 17:28
 */
@RestController
public class AiController {

    private final AiService aiService;
    private final ConversationService conversationService;
    private final KnowledgeBaseService knowledgeBaseService;

    public AiController(AiService aiService, ConversationService conversationService, KnowledgeBaseService knowledgeBaseService) {
        this.aiService = aiService;
        this.conversationService = conversationService;
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
    public Flux<String> chatByOllama(@RequestBody String question,
                                     @RequestParam("conversationId") UUID conversationId,
                                     @RequestParam(value = "model", defaultValue = "qwen") String model) {
        return aiService.chatByStream(question, conversationId, model);
    }

    /**
     * RAG 对话接口
     *
     * @param question 问题
     * @return 流式回复
     */
    @PostMapping(value = "/ai/chatByRag", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> chatByRag(@RequestBody String question,
                                  @RequestParam("conversationId") UUID conversationId,
                                  @RequestParam(value = "model", defaultValue = "qwen") String model) {
        return aiService.chatByRag(question, conversationId, model);
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

    @PostMapping("/ai/conversations")
    public Map<String, Object> createConversation(@RequestParam(value = "title", required = false) String title) {
        String t = (title == null || title.isBlank()) ? "新的对话" : title;
        UUID id = conversationService.createConversation(t);
        return Map.of("id", id.toString(), "title", t);
    }

    @GetMapping("/ai/conversations")
    public Object listConversations() {
        return conversationService.listConversations();
    }

    @GetMapping("/ai/conversations/{id}/messages")
    public Object listMessages(@PathVariable("id") UUID id, @RequestParam(value = "limit", defaultValue = "200") int limit) {
        return conversationService.listMessages(id, limit);
    }

    @PutMapping("/ai/conversations/{id}/title")
    public Map<String, Object> renameConversation(@PathVariable("id") UUID id, @RequestParam("title") String title) {
        conversationService.renameConversation(id, title);
        return Map.of("ok", true);
    }

    @DeleteMapping("/ai/conversations/{id}")
    public Map<String, Object> deleteConversation(@PathVariable("id") UUID id) {
        conversationService.deleteConversation(id);
        return Map.of("ok", true);
    }
}
