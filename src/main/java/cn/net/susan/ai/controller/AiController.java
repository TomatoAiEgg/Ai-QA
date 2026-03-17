package cn.net.susan.ai.controller;

import cn.net.susan.ai.service.AiService;
import cn.net.susan.ai.service.ConversationService;
import cn.net.susan.ai.service.KnowledgeBaseService;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.ModelAndView;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
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
    private final TokenTextSplitter tokenTextSplitter;

    public AiController(AiService aiService, ConversationService conversationService, KnowledgeBaseService knowledgeBaseService, TokenTextSplitter tokenTextSplitter) {
        this.aiService = aiService;
        this.conversationService = conversationService;
        this.knowledgeBaseService = knowledgeBaseService;
        this.tokenTextSplitter = tokenTextSplitter;
    }

    /**
     * 访问苏三 AI 问答助手首页
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
     * 访问 RAG 知识库管理页面
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
     * AI 对话接口
     *
     * @param question 问题
     * @return 流式回复
     */
    @PostMapping(value = "/ai/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> chat(@RequestBody String question,
                             @RequestParam("conversationId") UUID conversationId) {
        return aiService.chatByStream(question, conversationId, "qwen")
                .map(text -> ServerSentEvent.builder(text).build());
    }

    /**
     * RAG 对话接口
     *
     * @param question 问题
     * @return 流式回复
     */
    @PostMapping(value = "/ai/chatByRag", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> chatByRag(@RequestBody String question,
                                  @RequestParam("conversationId") UUID conversationId,
                                  @RequestParam(value = "model", defaultValue = "qwen") String model) {
        return aiService.chatByRag(question, conversationId, model)
                .map(text -> ServerSentEvent.builder(text).build());
    }

    /**
     * 上传文档到知识库
     *
     * @param file 文件
     * @param kbId 知识库 ID（可选）
     * @return 上传结果
     */
    @PostMapping("/ai/upload")
    public String upload(@RequestParam("file") MultipartFile file,
                         @RequestParam(value = "kbId", required = false) UUID kbId) {
        return knowledgeBaseService.uploadDocument(file, kbId);
    }

    @GetMapping("/ai/documents")
    public Object getDocuments() {
        return knowledgeBaseService.getDocumentList();
    }

    /**
     * 预览文档切片效果
     * 上传文件后先不存储，仅返回切片结果用于预览
     *
     * @param file 文件
     * @return 切片结果列表
     */
    @PostMapping("/ai/preview-chunks")
    public Object previewChunks(@RequestParam("file") MultipartFile file) {
        try {
            // 1. 保存文件到临时目录
            Path tempFile = Files.createTempFile("kb_preview_", file.getOriginalFilename());
            Files.copy(file.getInputStream(), tempFile, StandardCopyOption.REPLACE_EXISTING);

            // 2. 使用 Tika 读取文档内容
            TikaDocumentReader reader = new TikaDocumentReader(new org.springframework.core.io.FileSystemResource(tempFile));
            List<Document> documents = reader.read();

            // 3. 文本切分
            List<Document> splitDocuments = tokenTextSplitter.apply(documents);

            // 4. 清理临时文件
            Files.deleteIfExists(tempFile);

            // 5. 返回切片预览信息
            return splitDocuments.stream()
                    .map(doc -> Map.of(
                            "index", splitDocuments.indexOf(doc),
                            "content", doc.getContent(),
                            "length", doc.getContent().length()
                    ))
                    .toList();

        } catch (IOException e) {
            return Map.of("error", "文件处理失败：" + e.getMessage());
        }
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
