package cn.net.tomatoegg.ai.controller.conversation;

import cn.net.tomatoegg.ai.common.ApiCode;
import cn.net.tomatoegg.ai.common.ResponseUtils;
import cn.net.tomatoegg.ai.exception.BusinessException;
import cn.net.tomatoegg.ai.service.auth.AuthService;
import cn.net.tomatoegg.ai.service.conversation.ConversationService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/ai/conversations")
public class ConversationController {

    private final AuthService authService;
    private final ConversationService conversationService;

    public ConversationController(AuthService authService, ConversationService conversationService) {
        this.authService = authService;
        this.conversationService = conversationService;
    }

    @PostMapping
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

    @GetMapping
    public Map<String, Object> listConversations() {
        try {
            return ResponseUtils.success(conversationService.listConversations(authService.getCurrentUserId()));
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException(ApiCode.CONVERSATION_LIST_FAILED, ex);
        }
    }

    @GetMapping("/{id}/messages")
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

    @PutMapping("/{id}/title")
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

    @DeleteMapping("/{id}")
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
