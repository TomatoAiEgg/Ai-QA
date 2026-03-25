package cn.net.tomatoegg.ai.service;

import cn.net.tomatoegg.ai.common.ApiCode;
import cn.net.tomatoegg.ai.entity.KnowledgeBase;
import cn.net.tomatoegg.ai.entity.KnowledgeBaseDocument;
import cn.net.tomatoegg.ai.entity.VectorStoreChunk;
import cn.net.tomatoegg.ai.exception.BusinessException;
import cn.net.tomatoegg.ai.mapper.KnowledgeBaseDocumentMapper;
import cn.net.tomatoegg.ai.mapper.KnowledgeBaseMapper;
import cn.net.tomatoegg.ai.mapper.VectorStoreMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
public class KnowledgeBaseService {

    private static final Path STORAGE_ROOT = Paths.get("data", "kb-files");

    private final TokenTextSplitter tokenTextSplitter;
    private final EmbeddingModel embeddingModel;
    private final ObjectMapper objectMapper;
    private final KnowledgeBaseMapper knowledgeBaseMapper;
    private final KnowledgeBaseDocumentMapper documentMapper;
    private final VectorStoreMapper vectorStoreMapper;

    public KnowledgeBaseService(TokenTextSplitter tokenTextSplitter,
                                EmbeddingModel embeddingModel,
                                ObjectMapper objectMapper,
                                KnowledgeBaseMapper knowledgeBaseMapper,
                                KnowledgeBaseDocumentMapper documentMapper,
                                VectorStoreMapper vectorStoreMapper) {
        this.tokenTextSplitter = tokenTextSplitter;
        this.embeddingModel = embeddingModel;
        this.objectMapper = objectMapper;
        this.knowledgeBaseMapper = knowledgeBaseMapper;
        this.documentMapper = documentMapper;
        this.vectorStoreMapper = vectorStoreMapper;
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
        log.info("开始上传文档, userId={}, kbId={}, filename={}, size={}", userId, kbId, file.getOriginalFilename(), file.getSize());

        Path storedFile = saveUploadedFile(file, userId);
        KnowledgeBaseDocument docRecord = KnowledgeBaseDocument.builder()
                .id(UUID.randomUUID())
                .kbId(kbId)
                .userId(userId)
                .filename(file.getOriginalFilename())
                .storagePath(storedFile.toAbsolutePath().toString())
                .fileSize(file.getSize())
                .fileHash(calculateFileHash(file))
                .chunkCount(0)
                .status("PROCESSING")
                .build();
        documentMapper.insert(docRecord);

        try {
            List<Document> documents = readDocuments(storedFile);
            int totalOriginalChars = documents.stream().mapToInt(doc -> doc.getContent().length()).sum();
            List<Document> splitDocuments = tokenTextSplitter.apply(documents);

            for (int i = 0; i < splitDocuments.size(); i++) {
                Document document = splitDocuments.get(i);
                document.getMetadata().put("doc_id", docRecord.getId().toString());
                document.getMetadata().put("filename", file.getOriginalFilename());
                document.getMetadata().put("file_hash", docRecord.getFileHash());
                document.getMetadata().put("uploadTime", System.currentTimeMillis());
                document.getMetadata().put("kb_id", kbId.toString());
                document.getMetadata().put("user_id", userId.toString());
                document.getMetadata().put("chunkIndex", i);
                document.getMetadata().put("totalChunks", splitDocuments.size());
            }

            for (int i = 0; i < splitDocuments.size(); i++) {
                Document document = splitDocuments.get(i);
                vectorStoreMapper.insertChunk(
                        UUID.randomUUID(),
                        userId,
                        kbId,
                        docRecord.getId(),
                        i,
                        document.getContent(),
                        toMetadataJson(document.getMetadata()),
                        toVectorLiteral(embeddingModel.embed(document))
                );
            }

            Map<String, Object> stats = analyzeChunks(splitDocuments, totalOriginalChars);
            documentMapper.update(null, new LambdaUpdateWrapper<KnowledgeBaseDocument>()
                    .eq(KnowledgeBaseDocument::getId, docRecord.getId())
                    .set(KnowledgeBaseDocument::getChunkCount, splitDocuments.size())
                    .set(KnowledgeBaseDocument::getStatus, "COMPLETED")
                    .set(KnowledgeBaseDocument::getErrorMessage, null)
                    .setSql("updated_at = NOW()"));
            log.info("文档上传处理成功, userId={}, kbId={}, docId={}, filename={}, chunkCount={}",
                    userId, kbId, docRecord.getId(), file.getOriginalFilename(), splitDocuments.size());

            return String.format(
                    "文档上传并处理成功，共生成 %d 个片段，平均每个片段 %d 字符，重叠率 %.1f%%",
                    splitDocuments.size(),
                    stats.get("avgChunkSize"),
                    stats.get("overlapRatio")
            );
        } catch (Exception e) {
            log.error("文档处理失败, userId={}, kbId={}, docId={}, filename={}",
                    userId, kbId, docRecord.getId(), file.getOriginalFilename(), e);
            documentMapper.update(null, new LambdaUpdateWrapper<KnowledgeBaseDocument>()
                    .eq(KnowledgeBaseDocument::getId, docRecord.getId())
                    .set(KnowledgeBaseDocument::getStatus, "FAILED")
                    .set(KnowledgeBaseDocument::getErrorMessage, e.getMessage())
                    .setSql("updated_at = NOW()"));
            deleteStoredFile(docRecord.getStoragePath());
            throw new BusinessException(ApiCode.DOCUMENT_UPLOAD_FAILED, "文档处理失败: " + e.getMessage(), e);
        }
    }

    public String uploadDocument(MultipartFile file) {
        throw new BusinessException(ApiCode.BAD_REQUEST, "请先选择知识库");
    }

    public List<Document> searchFromKnowledgeBase(String query, UUID kbId, int topK, UUID userId) {
        String queryEmbedding = toVectorLiteral(embeddingModel.embed(query));
        List<VectorStoreChunk> chunks;
        if (kbId != null) {
            requireOwnedKnowledgeBase(userId, kbId);
            chunks = vectorStoreMapper.searchByKnowledgeBaseId(kbId, queryEmbedding, topK);
            log.info("RAG 检索完成, mode=single-kb, userId={}, kbId={}, topK={}, hitCount={}", userId, kbId, topK, chunks.size());
        } else {
            chunks = vectorStoreMapper.searchByUserId(userId, queryEmbedding, topK);
            log.info("RAG 检索完成, mode=all-kb, userId={}, topK={}, hitCount={}", userId, topK, chunks.size());
        }
        return chunks.stream()
                .map(this::toDocument)
                .collect(Collectors.toList());
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

    @Cacheable(value = "documentPreviewCache", key = "#userId.toString() + ':' + #docId.toString()")
    public List<Map<String, Object>> previewDocument(UUID docId, UUID userId) {
        KnowledgeBaseDocument document = requireOwnedDocument(userId, docId);
        if (document.getStoragePath() == null || document.getStoragePath().isBlank()) {
            throw new BusinessException(ApiCode.BAD_REQUEST, "当前文档没有可预览的原始文件");
        }

        Path storedFile = Path.of(document.getStoragePath());
        if (!Files.exists(storedFile)) {
            throw new BusinessException(ApiCode.NOT_FOUND, "原始文件不存在，无法预览");
        }

        List<Document> splitDocuments = tokenTextSplitter.apply(readDocuments(storedFile));
        return splitDocuments.stream()
                .map(item -> Map.<String, Object>of(
                        "index", splitDocuments.indexOf(item),
                        "content", item.getContent(),
                        "length", item.getContent().length()
                ))
                .toList();
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
        vectorStoreMapper.deleteByDocumentId(docId.toString());
        deleteStoredFile(document.getStoragePath());
        documentMapper.deleteById(docId);
        log.info("删除文档成功, userId={}, kbId={}, docId={}, filename={}", userId, document.getKbId(), docId, document.getFilename());
    }

    public void deleteKnowledgeBaseVectors(UUID kbId) {
        vectorStoreMapper.deleteByKnowledgeBaseId(kbId.toString());
        log.info("删除知识库向量数据成功, kbId={}", kbId);
    }

    public void deleteStoredFile(String storagePath) {
        if (storagePath == null || storagePath.isBlank()) {
            return;
        }
        try {
            Files.deleteIfExists(Path.of(storagePath));
        } catch (IOException e) {
            log.warn("删除原始文档失败: {}", storagePath, e);
        }
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

    private String calculateFileHash(MultipartFile file) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(file.getBytes());
            StringBuilder hexString = new StringBuilder(2 * hash.length);
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            log.warn("计算文件哈希失败", e);
            return null;
        }
    }

    private Path saveUploadedFile(MultipartFile file, UUID userId) {
        try {
            Path userStorageRoot = STORAGE_ROOT.resolve(userId.toString());
            Files.createDirectories(userStorageRoot);
            String originalName = file.getOriginalFilename() == null ? "document" : file.getOriginalFilename();
            String storedName = UUID.randomUUID() + "_" + originalName.replaceAll("[\\\\/:*?\"<>|]", "_");
            Path target = userStorageRoot.resolve(storedName);
            Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
            log.info("保存原始文档成功, userId={}, path={}", userId, target.toAbsolutePath());
            return target;
        } catch (IOException e) {
            throw new BusinessException(ApiCode.DOCUMENT_UPLOAD_FAILED, "保存上传文档失败: " + e.getMessage(), e);
        }
    }

    private List<Document> readDocuments(Path filePath) {
        TikaDocumentReader reader = new TikaDocumentReader(new org.springframework.core.io.FileSystemResource(filePath));
        return reader.read();
    }

    private String toMetadataJson(Map<String, Object> metadata) {
        try {
            return objectMapper.writeValueAsString(metadata);
        } catch (JsonProcessingException e) {
            throw new BusinessException(ApiCode.SERVER_ERROR, "序列化向量元数据失败", e);
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

    private Document toDocument(VectorStoreChunk chunk) {
        try {
            Map<String, Object> metadata = objectMapper.readValue(chunk.getMetadataJson(), new TypeReference<>() {});
            Document document = new Document(chunk.getContent(), metadata);
            metadata.put("score", chunk.getScore());
            return document;
        } catch (JsonProcessingException e) {
            throw new BusinessException(ApiCode.SERVER_ERROR, "解析向量元数据失败", e);
        }
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
