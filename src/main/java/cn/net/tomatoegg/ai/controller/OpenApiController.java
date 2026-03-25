package cn.net.tomatoegg.ai.controller;

import cn.net.tomatoegg.ai.common.ApiCode;
import cn.net.tomatoegg.ai.common.ResponseUtils;
import cn.net.tomatoegg.ai.exception.BusinessException;
import cn.net.tomatoegg.ai.service.KnowledgeBaseService;
import cn.net.tomatoegg.ai.service.OpenApiTokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/openapi")
@RequiredArgsConstructor
public class OpenApiController {

    private final OpenApiTokenService openApiTokenService;
    private final KnowledgeBaseService knowledgeBaseService;

    @PostMapping("/knowledge-bases/{kbId}/documents")
    public Map<String, Object> uploadDocument(@PathVariable UUID kbId,
                                              @RequestParam("file") MultipartFile file,
                                              @RequestHeader(value = "Authorization", required = false) String authorization,
                                              @RequestHeader(value = "X-API-Token", required = false) String apiTokenHeader) {
        try {
            UUID userId = openApiTokenService.authenticate(resolveToken(authorization, apiTokenHeader));
            String message = knowledgeBaseService.uploadDocument(file, kbId, userId);
            return ResponseUtils.success(Map.of(
                    "kbId", kbId.toString(),
                    "filename", file.getOriginalFilename() == null ? "" : file.getOriginalFilename(),
                    "message", message
            ));
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException(ApiCode.OPEN_API_UPLOAD_FAILED, ex);
        }
    }

    private String resolveToken(String authorization, String apiTokenHeader) {
        if (apiTokenHeader != null && !apiTokenHeader.isBlank()) {
            return apiTokenHeader.trim();
        }
        if (authorization == null || authorization.isBlank()) {
            return null;
        }
        String trimmed = authorization.trim();
        if (trimmed.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return trimmed.substring(7).trim();
        }
        return trimmed;
    }
}
