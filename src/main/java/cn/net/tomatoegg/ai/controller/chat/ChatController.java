package cn.net.tomatoegg.ai.controller.chat;

import cn.net.tomatoegg.ai.common.ApiCode;
import cn.net.tomatoegg.ai.exception.BusinessException;
import cn.net.tomatoegg.ai.service.auth.AuthService;
import cn.net.tomatoegg.ai.service.chat.ChatService;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/ai")
public class ChatController {

    private final ChatService chatService;
    private final AuthService authService;

    public ChatController(ChatService chatService, AuthService authService) {
        this.chatService = chatService;
        this.authService = authService;
    }

    @PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> chat(@RequestBody Map<String, Object> request) {
        try {
            UUID userId = authService.getCurrentUserId();
            String question = (String) request.get("question");
            String conversationIdStr = (String) request.get("conversationId");
            UUID conversationId = (conversationIdStr != null && !conversationIdStr.isBlank())
                    ? UUID.fromString(conversationIdStr)
                    : null;
            String model = (String) request.getOrDefault("model", "qwen");
            return chatService.chatByStream(question, userId, conversationId, model)
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

    @PostMapping(value = "/chatByRag", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
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
            return chatService.chatByRag(question, userId, conversationId, kbId, model)
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
}
