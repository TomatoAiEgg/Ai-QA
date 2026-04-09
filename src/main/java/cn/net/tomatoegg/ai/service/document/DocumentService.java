package cn.net.tomatoegg.ai.service.document;

import cn.net.tomatoegg.ai.common.ApiCode;
import cn.net.tomatoegg.ai.entity.KnowledgeBase;
import cn.net.tomatoegg.ai.entity.KnowledgeBaseDocument;
import cn.net.tomatoegg.ai.exception.BusinessException;
import cn.net.tomatoegg.ai.mapper.KnowledgeBaseDocumentMapper;
import cn.net.tomatoegg.ai.mapper.KnowledgeBaseMapper;
import cn.net.tomatoegg.ai.mapper.VectorStoreMapper;
import cn.net.tomatoegg.ai.service.document.storage.DocumentPreviewResource;
import cn.net.tomatoegg.ai.service.document.storage.DocumentStorageService;
import cn.net.tomatoegg.ai.service.document.storage.StoredDocument;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
public class DocumentService {

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("txt", "md", "pdf", "doc", "docx");
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            MediaType.TEXT_PLAIN_VALUE,
            "text/markdown",
            "application/pdf",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
    );

    private final KnowledgeBaseMapper knowledgeBaseMapper;
    private final KnowledgeBaseDocumentMapper documentMapper;
    private final VectorStoreMapper vectorStoreMapper;
    private final DocumentProcessingService documentProcessingService;
    private final DocumentProcessDispatcher documentProcessDispatcher;
    private final DocumentStorageService documentStorageService;
    private final CacheManager cacheManager;

    public DocumentService(KnowledgeBaseMapper knowledgeBaseMapper,
                           KnowledgeBaseDocumentMapper documentMapper,
                           VectorStoreMapper vectorStoreMapper,
                           DocumentProcessingService documentProcessingService,
                           DocumentProcessDispatcher documentProcessDispatcher,
                           DocumentStorageService documentStorageService,
                           CacheManager cacheManager) {
        this.knowledgeBaseMapper = knowledgeBaseMapper;
        this.documentMapper = documentMapper;
        this.vectorStoreMapper = vectorStoreMapper;
        this.documentProcessingService = documentProcessingService;
        this.documentProcessDispatcher = documentProcessDispatcher;
        this.documentStorageService = documentStorageService;
        this.cacheManager = cacheManager;
    }

    @Caching(evict = {
            @CacheEvict(value = "knowledgeBaseListCache", key = "#userId.toString()"),
            @CacheEvict(value = "knowledgeBaseDetailCache", allEntries = true),
            @CacheEvict(value = "knowledgeBaseDocumentsCache", allEntries = true),
            @CacheEvict(value = "documentListCache", allEntries = true),
            @CacheEvict(value = "documentPreviewCache", allEntries = true)
    })
    public String uploadDocument(MultipartFile file, UUID kbId, UUID userId) {
        if (kbId == null) {
            throw new BusinessException(ApiCode.BAD_REQUEST, "请先选择知识库");
        }

        requireOwnedKnowledgeBase(userId, kbId);
        validateUploadableFile(file);
        String safeFilename = resolveSafeOriginalFilename(file);
        log.info("Start uploading document, userId={}, kbId={}, filename={}, size={}",
                userId, kbId, safeFilename, file.getSize());

        StoredDocument storedDocument = documentStorageService.store(file, userId, safeFilename);
        KnowledgeBaseDocument docRecord = KnowledgeBaseDocument.builder()
                .id(UUID.randomUUID())
                .kbId(kbId)
                .userId(userId)
                .filename(safeFilename)
                .storagePath(storedDocument.storagePath())
                .fileSize(file.getSize())
                .fileHash(storedDocument.fileHash())
                .chunkCount(0)
                .status(DocumentProcessStatus.PENDING)
                .build();

        try {
            documentMapper.insert(docRecord);
        } catch (Exception ex) {
            deleteStoredFile(storedDocument.storagePath());
            throw ex instanceof BusinessException
                    ? (BusinessException) ex
                    : new BusinessException(ApiCode.DOCUMENT_UPLOAD_FAILED, "保存文档记录失败: " + ex.getMessage(), ex);
        }

        try {
            return documentProcessDispatcher.dispatch(docRecord.getId());
        } catch (Exception ex) {
            updateDocumentStatus(docRecord.getId(), DocumentProcessStatus.FAILED, 0, ex.getMessage());
            evictDocumentCaches(userId);
            throw ex instanceof BusinessException
                    ? (BusinessException) ex
                    : new BusinessException(ApiCode.DOCUMENT_UPLOAD_FAILED, "文档上传失败: " + ex.getMessage(), ex);
        }
    }

    public void validateUploadableFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ApiCode.BAD_REQUEST, "上传文件不能为空");
        }

        String safeFilename = resolveSafeOriginalFilename(file);
        String extension = resolveExtension(safeFilename);
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new BusinessException(ApiCode.BAD_REQUEST, "仅支持上传 txt、md、pdf、doc、docx 格式的文件");
        }

        String contentType = file.getContentType();
        if (StringUtils.hasText(contentType)
                && !ALLOWED_CONTENT_TYPES.contains(contentType)
                && !MediaType.APPLICATION_OCTET_STREAM_VALUE.equalsIgnoreCase(contentType)) {
            throw new BusinessException(ApiCode.BAD_REQUEST, "文件类型不受支持，请上传文本、Word 或 PDF 文件");
        }
    }

    public String resolveSafeOriginalFilename(MultipartFile file) {
        String originalName = file == null ? null : file.getOriginalFilename();
        if (!StringUtils.hasText(originalName)) {
            return "document";
        }

        String normalized = originalName.replace('\\', '/');
        int lastSlashIndex = normalized.lastIndexOf('/');
        String filename = lastSlashIndex >= 0 ? normalized.substring(lastSlashIndex + 1) : normalized;
        filename = filename.trim();
        return filename.isEmpty() ? "document" : filename;
    }

    public String retryDocument(UUID docId, UUID userId) {
        KnowledgeBaseDocument document = requireOwnedDocument(userId, docId);
        if (DocumentProcessStatus.COMPLETED.equalsIgnoreCase(document.getStatus())) {
            return "文档已经处理完成";
        }
        if (DocumentProcessStatus.PROCESSING.equalsIgnoreCase(document.getStatus())) {
            return "文档正在处理中";
        }
        if (DocumentProcessStatus.PENDING.equalsIgnoreCase(document.getStatus())) {
            return "文档已经进入处理队列";
        }

        updateDocumentStatus(docId, DocumentProcessStatus.PENDING, 0, null);
        try {
            String message = documentProcessDispatcher.dispatch(docId);
            evictDocumentCaches(userId);
            return message;
        } catch (Exception ex) {
            updateDocumentStatus(docId, DocumentProcessStatus.FAILED, 0, ex.getMessage());
            evictDocumentCaches(userId);
            throw ex instanceof BusinessException
                    ? (BusinessException) ex
                    : new BusinessException(ApiCode.DOCUMENT_RETRY_FAILED, "重试文档处理失败: " + ex.getMessage(), ex);
        }
    }

    @Cacheable(value = "documentListCache", key = "#userId.toString() + ':' + (#kbId == null ? 'all' : #kbId.toString())")
    public List<KnowledgeBaseDocument> getDocumentList(UUID kbId, UUID userId) {
        if (kbId != null) {
            requireOwnedKnowledgeBase(userId, kbId);
            return documentMapper.selectList(new LambdaQueryWrapper<KnowledgeBaseDocument>()
                    .eq(KnowledgeBaseDocument::getUserId, userId)
                    .eq(KnowledgeBaseDocument::getKbId, kbId)
                    .orderByDesc(KnowledgeBaseDocument::getCreatedAt));
        }

        return documentMapper.selectList(new LambdaQueryWrapper<KnowledgeBaseDocument>()
                .eq(KnowledgeBaseDocument::getUserId, userId)
                .orderByDesc(KnowledgeBaseDocument::getCreatedAt));
    }

    public DocumentPreviewResource previewDocument(UUID docId, UUID userId) {
        KnowledgeBaseDocument document = requireOwnedDocument(userId, docId);
        if (!StringUtils.hasText(document.getStoragePath())) {
            throw new BusinessException(ApiCode.BAD_REQUEST, "当前文档没有可预览的源文件");
        }
        return documentStorageService.openPreview(document.getStoragePath(), document.getFilename());
    }

    @Caching(evict = {
            @CacheEvict(value = "knowledgeBaseListCache", key = "#userId.toString()"),
            @CacheEvict(value = "knowledgeBaseDetailCache", allEntries = true),
            @CacheEvict(value = "knowledgeBaseDocumentsCache", allEntries = true),
            @CacheEvict(value = "documentListCache", allEntries = true),
            @CacheEvict(value = "documentPreviewCache", allEntries = true)
    })
    public void deleteDocument(UUID docId, UUID userId) {
        KnowledgeBaseDocument document = requireOwnedDocument(userId, docId);
        vectorStoreMapper.deleteByDocumentId(docId);
        deleteStoredFile(document.getStoragePath());
        documentMapper.deleteById(docId);
        log.info("Deleted document, userId={}, kbId={}, docId={}, filename={}",
                userId, document.getKbId(), docId, document.getFilename());
    }

    public void deleteKnowledgeBaseDocuments(UUID userId, UUID kbId) {
        documentProcessingService.deleteKnowledgeBaseVectors(kbId);
        documentMapper.selectList(new LambdaQueryWrapper<KnowledgeBaseDocument>()
                        .eq(KnowledgeBaseDocument::getUserId, userId)
                        .eq(KnowledgeBaseDocument::getKbId, kbId))
                .forEach(doc -> deleteStoredFile(doc.getStoragePath()));
        documentMapper.delete(new LambdaQueryWrapper<KnowledgeBaseDocument>()
                .eq(KnowledgeBaseDocument::getUserId, userId)
                .eq(KnowledgeBaseDocument::getKbId, kbId));
    }

    public void deleteStoredFile(String storagePath) {
        if (!StringUtils.hasText(storagePath)) {
            return;
        }
        try {
            documentStorageService.delete(storagePath);
        } catch (BusinessException ex) {
            log.warn("删除源文件失败: {}", storagePath, ex);
        }
    }

    private void updateDocumentStatus(UUID docId, String status, int chunkCount, String errorMessage) {
        documentMapper.update(null, new LambdaUpdateWrapper<KnowledgeBaseDocument>()
                .eq(KnowledgeBaseDocument::getId, docId)
                .set(KnowledgeBaseDocument::getChunkCount, chunkCount)
                .set(KnowledgeBaseDocument::getStatus, status)
                .set(KnowledgeBaseDocument::getErrorMessage, errorMessage)
                .setSql("updated_at = NOW()"));
    }

    private void evictDocumentCaches(UUID userId) {
        evictSingleCacheEntry("knowledgeBaseListCache", userId.toString());
        clearCache("knowledgeBaseDetailCache");
        clearCache("knowledgeBaseDocumentsCache");
        clearCache("documentListCache");
        clearCache("documentPreviewCache");
    }

    private void clearCache(String cacheName) {
        Cache cache = cacheManager.getCache(cacheName);
        if (cache != null) {
            cache.clear();
        }
    }

    private void evictSingleCacheEntry(String cacheName, String key) {
        Cache cache = cacheManager.getCache(cacheName);
        if (cache != null) {
            cache.evict(key);
        }
    }

    private String resolveExtension(String filename) {
        int lastDotIndex = filename.lastIndexOf('.');
        if (lastDotIndex < 0 || lastDotIndex == filename.length() - 1) {
            return "";
        }
        return filename.substring(lastDotIndex + 1).toLowerCase(Locale.ROOT);
    }

    private KnowledgeBase requireOwnedKnowledgeBase(UUID userId, UUID kbId) {
        KnowledgeBase knowledgeBase = knowledgeBaseMapper.selectOne(new LambdaQueryWrapper<KnowledgeBase>()
                .eq(KnowledgeBase::getId, kbId)
                .eq(KnowledgeBase::getCreatedBy, userId)
                .last("LIMIT 1"));
        if (knowledgeBase == null) {
            throw new BusinessException(ApiCode.NOT_FOUND, "知识库不存在或无权访问");
        }
        return knowledgeBase;
    }

    private KnowledgeBaseDocument requireOwnedDocument(UUID userId, UUID docId) {
        KnowledgeBaseDocument document = documentMapper.selectOne(new LambdaQueryWrapper<KnowledgeBaseDocument>()
                .eq(KnowledgeBaseDocument::getId, docId)
                .eq(KnowledgeBaseDocument::getUserId, userId)
                .last("LIMIT 1"));
        if (document == null) {
            throw new BusinessException(ApiCode.NOT_FOUND, "文档记录不存在");
        }
        if (document.getKbId() == null) {
            throw new BusinessException(ApiCode.NOT_FOUND, "文档未关联知识库");
        }
        requireOwnedKnowledgeBase(userId, document.getKbId());
        return document;
    }
}
