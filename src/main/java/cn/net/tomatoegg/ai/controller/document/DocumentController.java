package cn.net.tomatoegg.ai.controller.document;

import cn.net.tomatoegg.ai.common.ResponseUtils;
import cn.net.tomatoegg.ai.service.auth.AuthService;
import cn.net.tomatoegg.ai.service.document.DocumentService;
import cn.net.tomatoegg.ai.service.document.storage.DocumentPreviewResource;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/ai")
@RequiredArgsConstructor
public class DocumentController {

    private final AuthService authService;
    private final DocumentService documentService;

    @PostMapping("/upload")
    public Map<String, Object> upload(@RequestParam("file") MultipartFile file,
                                      @RequestParam(value = "kbId", required = false) UUID kbId) {
        return ResponseUtils.success(documentService.uploadDocument(file, kbId, authService.getCurrentUserId()));
    }

    @GetMapping("/documents")
    public Map<String, Object> getDocuments(@RequestParam(value = "kbId", required = false) UUID kbId) {
        return ResponseUtils.success(documentService.getDocumentList(kbId, authService.getCurrentUserId()));
    }

    @DeleteMapping("/documents/{id}")
    public Map<String, Object> deleteDocument(@PathVariable("id") UUID id) {
        documentService.deleteDocument(id, authService.getCurrentUserId());
        return ResponseUtils.success();
    }

    @PostMapping("/documents/{id}/retry")
    public Map<String, Object> retryDocument(@PathVariable("id") UUID id) {
        return ResponseUtils.success(documentService.retryDocument(id, authService.getCurrentUserId()));
    }

    @GetMapping("/documents/{id}/preview")
    public ResponseEntity<Resource> previewDocument(@PathVariable("id") UUID id) {
        DocumentPreviewResource previewFile = documentService.previewDocument(id, authService.getCurrentUserId());
        if (previewFile.redirectUrl() != null && !previewFile.redirectUrl().isBlank()) {
            return ResponseEntity.status(HttpStatus.FOUND)
                    .header(HttpHeaders.LOCATION, previewFile.redirectUrl())
                    .build();
        }

        return ResponseEntity.ok()
                .contentType(previewFile.mediaType())
                .contentLength(previewFile.contentLength())
                .cacheControl(CacheControl.maxAge(5, TimeUnit.MINUTES).cachePrivate())
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline()
                        .filename(previewFile.filename(), StandardCharsets.UTF_8)
                        .build()
                        .toString())
                .header("X-Content-Type-Options", "nosniff")
                .body(previewFile.resource());
    }
}
