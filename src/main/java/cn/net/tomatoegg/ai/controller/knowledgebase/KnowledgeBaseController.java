package cn.net.tomatoegg.ai.controller.knowledgebase;

import cn.net.tomatoegg.ai.common.ApiCode;
import cn.net.tomatoegg.ai.common.ResponseUtils;
import cn.net.tomatoegg.ai.exception.BusinessException;
import cn.net.tomatoegg.ai.service.auth.AuthService;
import cn.net.tomatoegg.ai.service.knowledgebase.KnowledgeBaseService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/knowledge-bases")
@RequiredArgsConstructor
public class KnowledgeBaseController {

    private final KnowledgeBaseService kbService;
    private final AuthService authService;

    @PostMapping
    public Map<String, Object> create(@RequestBody Map<String, String> request) {
        try {
            String name = request.get("name");
            String description = request.getOrDefault("description", "");
            return ResponseUtils.success(kbService.createKnowledgeBase(authService.getCurrentUserId(), name, description));
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException(ApiCode.KNOWLEDGE_BASE_CREATE_FAILED, ex);
        }
    }

    @GetMapping
    public Map<String, Object> list() {
        try {
            return ResponseUtils.success(kbService.listKnowledgeBases(authService.getCurrentUserId()));
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException(ApiCode.KNOWLEDGE_BASE_LIST_FAILED, ex);
        }
    }

    @GetMapping("/{id}")
    public Map<String, Object> get(@PathVariable UUID id) {
        try {
            return ResponseUtils.success(kbService.getKnowledgeBase(authService.getCurrentUserId(), id));
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException(ApiCode.KNOWLEDGE_BASE_GET_FAILED, ex);
        }
    }

    @PutMapping("/{id}")
    public Map<String, Object> update(@PathVariable UUID id, @RequestBody Map<String, String> request) {
        try {
            String name = request.get("name");
            String description = request.get("description");
            return ResponseUtils.success(kbService.updateKnowledgeBase(authService.getCurrentUserId(), id, name, description));
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException(ApiCode.KNOWLEDGE_BASE_UPDATE_FAILED, ex);
        }
    }

    @DeleteMapping("/{id}")
    public Map<String, Object> delete(@PathVariable UUID id) {
        try {
            kbService.deleteKnowledgeBase(authService.getCurrentUserId(), id);
            return ResponseUtils.success();
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException(ApiCode.KNOWLEDGE_BASE_DELETE_FAILED, ex);
        }
    }

    @GetMapping("/{id}/documents")
    public Map<String, Object> listDocuments(@PathVariable UUID id) {
        try {
            return ResponseUtils.success(kbService.listDocuments(authService.getCurrentUserId(), id));
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException(ApiCode.KNOWLEDGE_BASE_DOCUMENT_LIST_FAILED, ex);
        }
    }
}
