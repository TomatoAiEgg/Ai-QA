package cn.net.tomatoegg.ai.service.document;

import cn.net.tomatoegg.ai.common.ApiCode;
import cn.net.tomatoegg.ai.entity.KnowledgeBase;
import cn.net.tomatoegg.ai.entity.KnowledgeBaseDocument;
import cn.net.tomatoegg.ai.exception.BusinessException;
import cn.net.tomatoegg.ai.mapper.KnowledgeBaseDocumentMapper;
import cn.net.tomatoegg.ai.mapper.KnowledgeBaseMapper;
import cn.net.tomatoegg.ai.mapper.VectorStoreMapper;
import cn.net.tomatoegg.ai.mapper.param.VectorStoreChunkInsertParam;
import cn.net.tomatoegg.ai.service.document.storage.DocumentStorageService;
import cn.net.tomatoegg.ai.service.document.storage.StoredDocumentSource;
import cn.net.tomatoegg.ai.service.embedding.DashScopeEmbeddingService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.core.io.FileSystemResource;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
public class DocumentProcessingService {

    private static final int MAX_ERROR_MESSAGE_LENGTH = 500;
    private static final int MAX_EMBEDDING_BATCH_SIZE = 10;

    private final TokenTextSplitter tokenTextSplitter;
    private final DashScopeEmbeddingService embeddingService;
    private final ObjectMapper objectMapper;
    private final KnowledgeBaseMapper knowledgeBaseMapper;
    private final KnowledgeBaseDocumentMapper documentMapper;
    private final VectorStoreMapper vectorStoreMapper;
    private final DocumentStorageService documentStorageService;
    private final CacheManager cacheManager;
    private final int embeddingBatchSize;

    public DocumentProcessingService(TokenTextSplitter tokenTextSplitter,
                                     DashScopeEmbeddingService embeddingService,
                                     ObjectMapper objectMapper,
                                     KnowledgeBaseMapper knowledgeBaseMapper,
                                     KnowledgeBaseDocumentMapper documentMapper,
                                     VectorStoreMapper vectorStoreMapper,
                                     DocumentStorageService documentStorageService,
                                     CacheManager cacheManager,
                                     @Value("${ai.service.document.embedding-batch-size:10}") int embeddingBatchSize) {
        this.tokenTextSplitter = tokenTextSplitter;
        this.embeddingService = embeddingService;
        this.objectMapper = objectMapper;
        this.knowledgeBaseMapper = knowledgeBaseMapper;
        this.documentMapper = documentMapper;
        this.vectorStoreMapper = vectorStoreMapper;
        this.documentStorageService = documentStorageService;
        this.cacheManager = cacheManager;
        if (embeddingBatchSize > MAX_EMBEDDING_BATCH_SIZE) {
            log.warn("Configured embedding batch size {} exceeds provider limit {}, clamping to {}",
                    embeddingBatchSize, MAX_EMBEDDING_BATCH_SIZE, MAX_EMBEDDING_BATCH_SIZE);
        }
        this.embeddingBatchSize = Math.min(Math.max(1, embeddingBatchSize), MAX_EMBEDDING_BATCH_SIZE);
    }

    public String processDocument(UUID docId) {
        KnowledgeBaseDocument docRecord = documentMapper.selectById(docId);
        if (docRecord == null) {
            log.warn("Skip document processing because the document record is missing, docId={}", docId);
            return "文档记录不存在";
        }
        if (DocumentProcessStatus.COMPLETED.equalsIgnoreCase(docRecord.getStatus())) {
            log.info("Skip document processing because the document has already completed, docId={}", docId);
            return "文档已经处理完成";
        }
        if (!tryAcquireProcessing(docId)) {
            return describeSkippedProcessing(docId);
        }

        UUID userId = docRecord.getUserId();
        UUID kbId = docRecord.getKbId();
        try {
            requireOwnedKnowledgeBase(userId, kbId);

            if (docRecord.getStoragePath() == null || docRecord.getStoragePath().isBlank()) {
                throw new BusinessException(ApiCode.NOT_FOUND, "原始文件路径缺失");
            }

            List<Document> documents;
            int totalOriginalChars;
            List<Document> splitDocuments;
            try (StoredDocumentSource source =
                         documentStorageService.openProcessingSource(docRecord.getStoragePath(), docRecord.getFilename())) {
                documents = readDocuments(source.path());
                totalOriginalChars = documents.stream().mapToInt(doc -> doc.getContent().length()).sum();
                splitDocuments = tokenTextSplitter.apply(documents);
            }

            for (int i = 0; i < splitDocuments.size(); i++) {
                Document document = splitDocuments.get(i);
                document.getMetadata().put("doc_id", docRecord.getId().toString());
                document.getMetadata().put("filename", docRecord.getFilename());
                document.getMetadata().put("file_hash", docRecord.getFileHash());
                document.getMetadata().put("uploadTime", System.currentTimeMillis());
                document.getMetadata().put("kb_id", kbId.toString());
                document.getMetadata().put("user_id", userId.toString());
                document.getMetadata().put("chunkIndex", i);
                document.getMetadata().put("totalChunks", splitDocuments.size());
            }

            vectorStoreMapper.deleteByDocumentId(docRecord.getId());
            persistDocumentChunks(docRecord, splitDocuments);

            Map<String, Object> stats = analyzeChunks(splitDocuments, totalOriginalChars);
            updateDocumentStatus(docRecord.getId(), DocumentProcessStatus.COMPLETED, splitDocuments.size(), null);
            log.info("Document processing completed, userId={}, kbId={}, docId={}, filename={}, chunkCount={}",
                    userId, kbId, docRecord.getId(), docRecord.getFilename(), splitDocuments.size());

            return String.format(
                    "文档处理成功，共生成 %d 个切片，平均切片长度 %d 个字符，重叠率 %.1f%%",
                    splitDocuments.size(),
                    stats.get("avgChunkSize"),
                    stats.get("overlapRatio")
            );
        } catch (Exception ex) {
            log.error("Document processing failed, userId={}, kbId={}, docId={}, filename={}",
                    userId, kbId, docRecord.getId(), docRecord.getFilename(), ex);
            vectorStoreMapper.deleteByDocumentId(docRecord.getId());
            updateDocumentStatus(docRecord.getId(), DocumentProcessStatus.FAILED, 0, normalizeErrorMessage(ex));
            throw ex instanceof BusinessException
                    ? (BusinessException) ex
                    : new BusinessException(ApiCode.DOCUMENT_UPLOAD_FAILED, "文档处理失败: " + ex.getMessage(), ex);
        } finally {
            evictDocumentCaches(userId);
        }
    }

    public void deleteKnowledgeBaseVectors(UUID kbId) {
        vectorStoreMapper.deleteByKnowledgeBaseId(kbId);
        log.info("Deleted knowledge base vectors, kbId={}", kbId);
    }

    private boolean tryAcquireProcessing(UUID docId) {
        return documentMapper.update(null, new LambdaUpdateWrapper<KnowledgeBaseDocument>()
                .eq(KnowledgeBaseDocument::getId, docId)
                .and(wrapper -> wrapper.eq(KnowledgeBaseDocument::getStatus, DocumentProcessStatus.PENDING)
                        .or()
                        .eq(KnowledgeBaseDocument::getStatus, DocumentProcessStatus.FAILED))
                .set(KnowledgeBaseDocument::getStatus, DocumentProcessStatus.PROCESSING)
                .set(KnowledgeBaseDocument::getChunkCount, 0)
                .set(KnowledgeBaseDocument::getErrorMessage, null)
                .setSql("updated_at = NOW()")) > 0;
    }

    private String describeSkippedProcessing(UUID docId) {
        KnowledgeBaseDocument latest = documentMapper.selectById(docId);
        if (latest == null) {
            log.warn("Skip document processing because the document record is missing, docId={}", docId);
            return "文档记录不存在";
        }

        String status = latest.getStatus();
        if (DocumentProcessStatus.COMPLETED.equalsIgnoreCase(status)) {
            return "文档已经处理完成";
        }
        if (DocumentProcessStatus.PROCESSING.equalsIgnoreCase(status)) {
            log.info("Skip duplicate document processing because the document is already in progress, docId={}", docId);
            return "文档正在处理中";
        }
        if (DocumentProcessStatus.PENDING.equalsIgnoreCase(status)) {
            log.info("Skip document processing because the document is still pending, docId={}", docId);
            return "文档仍在排队中";
        }
        if (DocumentProcessStatus.FAILED.equalsIgnoreCase(status)) {
            log.info("Skip document processing because the document is marked as failed, docId={}", docId);
            return "文档处理失败";
        }

        log.warn("Skip document processing because document status is unexpected, docId={}, status={}", docId, status);
        return "文档状态异常";
    }

    private void updateDocumentStatus(UUID docId, String status, int chunkCount, String errorMessage) {
        documentMapper.update(null, new LambdaUpdateWrapper<KnowledgeBaseDocument>()
                .eq(KnowledgeBaseDocument::getId, docId)
                .set(KnowledgeBaseDocument::getChunkCount, chunkCount)
                .set(KnowledgeBaseDocument::getStatus, status)
                .set(KnowledgeBaseDocument::getErrorMessage, errorMessage)
                .setSql("updated_at = NOW()"));
    }

    private String normalizeErrorMessage(Exception ex) {
        String message = ex.getMessage();
        if (message == null || message.isBlank()) {
            return ex.getClass().getSimpleName();
        }
        if (message.length() <= MAX_ERROR_MESSAGE_LENGTH) {
            return message;
        }
        return message.substring(0, MAX_ERROR_MESSAGE_LENGTH);
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

    private List<Document> readDocuments(Path filePath) {
        TikaDocumentReader reader = new TikaDocumentReader(new FileSystemResource(filePath));
        return reader.read();
    }

    private void persistDocumentChunks(KnowledgeBaseDocument docRecord, List<Document> splitDocuments) {
        for (int start = 0; start < splitDocuments.size(); start += embeddingBatchSize) {
            int end = Math.min(start + embeddingBatchSize, splitDocuments.size());
            List<Document> batchDocuments = splitDocuments.subList(start, end);
            List<String> batchContents = batchDocuments.stream()
                    .map(Document::getContent)
                    .toList();
            List<float[]> embeddings = embeddingService.embedBatch(batchContents);

            if (embeddings.size() != batchDocuments.size()) {
                throw new BusinessException(ApiCode.SERVER_ERROR, "批量向量化结果数量与文档切片数量不一致");
            }

            List<VectorStoreChunkInsertParam> batchRows = new ArrayList<>(batchDocuments.size());
            for (int offset = 0; offset < batchDocuments.size(); offset++) {
                int chunkIndex = start + offset;
                Document document = batchDocuments.get(offset);
                batchRows.add(VectorStoreChunkInsertParam.builder()
                        .id(UUID.randomUUID())
                        .userId(docRecord.getUserId())
                        .kbId(docRecord.getKbId())
                        .docId(docRecord.getId())
                        .chunkIndex(chunkIndex)
                        .content(document.getContent())
                        .metadataJson(toMetadataJson(document.getMetadata()))
                        .embedding(toVectorLiteral(embeddings.get(offset)))
                        .build());
            }
            vectorStoreMapper.batchInsertChunks(batchRows);
        }
    }

    private String toMetadataJson(Map<String, Object> metadata) {
        try {
            return objectMapper.writeValueAsString(metadata);
        } catch (JsonProcessingException ex) {
            throw new BusinessException(ApiCode.SERVER_ERROR, "向量元数据序列化失败", ex);
        }
    }

    private String toVectorLiteral(float[] embedding) {
        StringBuilder builder = new StringBuilder("[");
        for (int i = 0; i < embedding.length; i++) {
            if (i > 0) {
                builder.append(',');
            }
            builder.append(embedding[i]);
        }
        builder.append(']');
        return builder.toString();
    }

    private Map<String, Object> analyzeChunks(List<Document> chunks, int totalOriginalChars) {
        Map<String, Object> stats = new HashMap<>();
        if (chunks.isEmpty()) {
            stats.put("avgChunkSize", 0);
            stats.put("overlapRatio", 0.0);
            return stats;
        }

        List<Integer> chunkSizes = chunks.stream().map(doc -> doc.getContent().length()).toList();
        int avgSize = chunkSizes.stream().mapToInt(Integer::intValue).sum() / chunkSizes.size();
        int totalChunkChars = chunkSizes.stream().mapToInt(Integer::intValue).sum();
        double overlapRatio = totalOriginalChars > 0
                ? ((double) totalChunkChars / totalOriginalChars - 1) * 100
                : 0;
        stats.put("avgChunkSize", avgSize);
        stats.put("overlapRatio", Math.round(overlapRatio * 10.0) / 10.0);
        return stats;
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
}
