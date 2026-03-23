package cn.net.tomatoegg.ai.controller;

import cn.net.tomatoegg.ai.common.ApiCode;
import cn.net.tomatoegg.ai.common.ResponseUtils;
import cn.net.tomatoegg.ai.entity.Conversation;
import cn.net.tomatoegg.ai.entity.ConversationMessage;
import cn.net.tomatoegg.ai.entity.KnowledgeBaseDocument;
import cn.net.tomatoegg.ai.exception.BusinessException;
import cn.net.tomatoegg.ai.service.AiService;
import cn.net.tomatoegg.ai.service.AuthService;
import cn.net.tomatoegg.ai.service.ConversationService;
import cn.net.tomatoegg.ai.service.KnowledgeBaseService;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.IntStream;

@RestController
public class AiController {

    private final AiService aiService;
    private final AuthService authService;
    private final ConversationService conversationService;
    private final KnowledgeBaseService knowledgeBaseService;
    private final TokenTextSplitter tokenTextSplitter;

    public AiController(AiService aiService,
                        AuthService authService,
                        ConversationService conversationService,
                        KnowledgeBaseService knowledgeBaseService,
                        TokenTextSplitter tokenTextSplitter) {
        this.aiService = aiService;
        this.authService = authService;
        this.conversationService = conversationService;
        this.knowledgeBaseService = knowledgeBaseService;
        this.tokenTextSplitter = tokenTextSplitter;
    }

    @PostMapping(value = "/ai/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> chat(@RequestBody Map<String, Object> request) {
        try {
            UUID userId = authService.getCurrentUserId();
            String question = (String) request.get("question");
            String conversationIdStr = (String) request.get("conversationId");
            UUID conversationId = (conversationIdStr != null && !conversationIdStr.isBlank())
                    ? UUID.fromString(conversationIdStr)
                    : null;
            String model = (String) request.getOrDefault("model", "qwen");
            return aiService.chatByStream(question, userId, conversationId, model)
                    .map(text -> ServerSentEvent.builder(text).build())
                    .onErrorMap(ex -> ex instanceof BusinessException
                            ? ex
                            : new BusinessException(ApiCode.CHAT_FAILED, ex.getMessage() == null ? ApiCode.CHAT_FAILED.getMessage() : ex.getMessage(), ex));
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException(ApiCode.CHAT_FAILED, ex);
        }
    }

    @PostMapping(value = "/ai/chatByRag", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> chatByRag(@RequestBody Map<String, Object> request) {
        try {
            UUID userId = authService.getCurrentUserId();
            String question = (String) request.get("question");
            String conversationIdStr = (String) request.get("conversationId");
            UUID conversationId = (conversationIdStr != null && !conversationIdStr.isBlank())
                    ? UUID.fromString(conversationIdStr)
                    : null;
            String kbIdStr = (String) request.get("kbId");
            UUID kbId = (kbIdStr != null && !kbIdStr.isBlank())
                    ? UUID.fromString(kbIdStr)
                    : null;
            String model = (String) request.getOrDefault("model", "qwen");
            return aiService.chatByRag(question, userId, conversationId, kbId, model)
                    .map(text -> ServerSentEvent.builder(text).build())
                    .onErrorMap(ex -> ex instanceof BusinessException
                            ? ex
                            : new BusinessException(ApiCode.RAG_CHAT_FAILED, ex.getMessage() == null ? ApiCode.RAG_CHAT_FAILED.getMessage() : ex.getMessage(), ex));
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException(ApiCode.RAG_CHAT_FAILED, ex);
        }
    }

    @PostMapping("/ai/upload")
    public Map<String, Object> upload(@RequestParam("file") MultipartFile file,
                                      @RequestParam(value = "kbId", required = false) UUID kbId) {
        try {
            return ResponseUtils.success(knowledgeBaseService.uploadDocument(file, kbId, authService.getCurrentUserId()));
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException(ApiCode.DOCUMENT_UPLOAD_FAILED, ex);
        }
    }

    @GetMapping("/ai/documents")
    public Map<String, Object> getDocuments(@RequestParam(value = "kbId", required = false) UUID kbId) {
        try {
            return ResponseUtils.success(knowledgeBaseService.getDocumentList(kbId, authService.getCurrentUserId()));
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException(ApiCode.DOCUMENT_LIST_FAILED, ex);
        }
    }

    @DeleteMapping("/ai/documents/{id}")
    public Map<String, Object> deleteDocument(@PathVariable("id") UUID id) {
        try {
            knowledgeBaseService.deleteDocument(id, authService.getCurrentUserId());
            return ResponseUtils.success();
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException(ApiCode.DOCUMENT_DELETE_FAILED, ex);
        }
    }

    @GetMapping("/ai/documents/{id}/preview")
    public Map<String, Object> previewDocument(@PathVariable("id") UUID id) {
        try {
            return ResponseUtils.success(knowledgeBaseService.previewDocument(id, authService.getCurrentUserId()));
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException(ApiCode.DOCUMENT_PREVIEW_FAILED, ex);
        }
    }

    @PostMapping("/ai/preview-chunks")
    public Map<String, Object> previewChunks(@RequestParam("file") MultipartFile file) {
        Path tempFile = null;
        try {
            tempFile = Files.createTempFile("kb_preview_", file.getOriginalFilename());
            Files.copy(file.getInputStream(), tempFile, StandardCopyOption.REPLACE_EXISTING);

            TikaDocumentReader reader = new TikaDocumentReader(new org.springframework.core.io.FileSystemResource(tempFile));
            List<Document> documents = reader.read();
            List<Document> splitDocuments = tokenTextSplitter.apply(documents);
            List<Map<String, Object>> previewItems = IntStream.range(0, splitDocuments.size())
                    .mapToObj(index -> Map.<String, Object>of(
                            "index", index,
                            "content", splitDocuments.get(index).getContent(),
                            "length", splitDocuments.get(index).getContent().length()
                    ))
                    .toList();
            return ResponseUtils.success(previewItems);
        } catch (BusinessException ex) {
            throw ex;
        } catch (IOException ex) {
            throw new BusinessException(ApiCode.CHUNK_PREVIEW_FAILED, "文件处理失败: " + ex.getMessage(), ex);
        } catch (Exception ex) {
            throw new BusinessException(ApiCode.CHUNK_PREVIEW_FAILED, ex);
        } finally {
            if (tempFile != null) {
                try {
                    Files.deleteIfExists(tempFile);
                } catch (IOException ignored) {
                }
            }
        }
    }

    @PostMapping("/ai/conversations")
    public Map<String, Object> createConversation(@RequestParam(value = "title", required = false) String title) {
        try {
            String resolvedTitle = (title == null || title.isBlank()) ? "新的对话" : title;
            UUID id = conversationService.createConversation(authService.getCurrentUserId(), resolvedTitle);
            return ResponseUtils.success(Map.of("id", id.toString(), "title", resolvedTitle));
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException(ApiCode.CONVERSATION_CREATE_FAILED, ex);
        }
    }

    @GetMapping("/ai/conversations")
    public Map<String, Object> listConversations() {
        try {
            return ResponseUtils.success(conversationService.listConversations(authService.getCurrentUserId()));
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException(ApiCode.CONVERSATION_LIST_FAILED, ex);
        }
    }

    @GetMapping("/ai/conversations/{id}/messages")
    public Map<String, Object> listMessages(@PathVariable("id") UUID id,
                                            @RequestParam(value = "limit", defaultValue = "200") int limit) {
        try {
            return ResponseUtils.success(conversationService.listMessages(authService.getCurrentUserId(), id, limit));
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException(ApiCode.CONVERSATION_MESSAGE_LIST_FAILED, ex);
        }
    }

    @PutMapping("/ai/conversations/{id}/title")
    public Map<String, Object> renameConversation(@PathVariable("id") UUID id,
                                                  @RequestBody(required = false) Map<String, String> body,
                                                  @RequestParam(value = "title", required = false) String title) {
        try {
            String resolvedTitle = title;
            if ((resolvedTitle == null || resolvedTitle.isBlank()) && body != null) {
                resolvedTitle = body.get("title");
            }
            if (resolvedTitle == null || resolvedTitle.isBlank()) {
                throw new BusinessException(ApiCode.BAD_REQUEST, "标题不能为空");
            }
            conversationService.renameConversation(authService.getCurrentUserId(), id, resolvedTitle);
            return ResponseUtils.success();
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException(ApiCode.CONVERSATION_RENAME_FAILED, ex);
        }
    }

    @DeleteMapping("/ai/conversations/{id}")
    public Map<String, Object> deleteConversation(@PathVariable("id") UUID id) {
        try {
            conversationService.deleteConversation(authService.getCurrentUserId(), id);
            return ResponseUtils.success();
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException(ApiCode.CONVERSATION_DELETE_FAILED, ex);
        }
    }
}
