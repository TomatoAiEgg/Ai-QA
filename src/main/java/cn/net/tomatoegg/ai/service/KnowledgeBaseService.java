package cn.net.tomatoegg.ai.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import cn.net.tomatoegg.ai.entity.KnowledgeBaseDocument;
import cn.net.tomatoegg.ai.mapper.KnowledgeBaseDocumentMapper;
import cn.net.tomatoegg.ai.mapper.VectorStoreMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
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

@Slf4j
@Service
public class KnowledgeBaseService {

    private static final Path STORAGE_ROOT = Paths.get("data", "kb-files");

    private final VectorStore vectorStore;
    private final TokenTextSplitter tokenTextSplitter;
    private final KnowledgeBaseDocumentMapper documentMapper;
    private final VectorStoreMapper vectorStoreMapper;

    public KnowledgeBaseService(VectorStore vectorStore,
                                TokenTextSplitter tokenTextSplitter,
                                KnowledgeBaseDocumentMapper documentMapper,
                                VectorStoreMapper vectorStoreMapper) {
        this.vectorStore = vectorStore;
        this.tokenTextSplitter = tokenTextSplitter;
        this.documentMapper = documentMapper;
        this.vectorStoreMapper = vectorStoreMapper;
    }

    public String uploadDocument(MultipartFile file, UUID kbId) {
        Path storedFile = saveUploadedFile(file);

        KnowledgeBaseDocument docRecord = KnowledgeBaseDocument.builder()
                .id(UUID.randomUUID())
                .kbId(kbId)
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
                document.getMetadata().put("kb_id", kbId != null ? kbId.toString() : "default");
                document.getMetadata().put("chunkIndex", i);
                document.getMetadata().put("totalChunks", splitDocuments.size());
            }

            int batchSize = 10;
            for (int i = 0; i < splitDocuments.size(); i += batchSize) {
                int end = Math.min(i + batchSize, splitDocuments.size());
                vectorStore.add(splitDocuments.subList(i, end));
            }

            Map<String, Object> stats = analyzeChunks(splitDocuments, totalOriginalChars);
            documentMapper.update(null, new LambdaUpdateWrapper<KnowledgeBaseDocument>()
                    .eq(KnowledgeBaseDocument::getId, docRecord.getId())
                    .set(KnowledgeBaseDocument::getChunkCount, splitDocuments.size())
                    .set(KnowledgeBaseDocument::getStatus, "COMPLETED")
                    .set(KnowledgeBaseDocument::getErrorMessage, null)
                    .setSql("updated_at = NOW()"));

            return String.format(
                    "文档上传并处理成功，共生成 %d 个片段(平均每个片段 %d 字符，重叠率 %.1f%%)",
                    splitDocuments.size(),
                    stats.get("avgChunkSize"),
                    stats.get("overlapRatio")
            );
        } catch (Exception e) {
            log.error("文档处理失败", e);
            documentMapper.update(null, new LambdaUpdateWrapper<KnowledgeBaseDocument>()
                    .eq(KnowledgeBaseDocument::getId, docRecord.getId())
                    .set(KnowledgeBaseDocument::getStatus, "FAILED")
                    .set(KnowledgeBaseDocument::getErrorMessage, e.getMessage())
                    .setSql("updated_at = NOW()"));
            deleteStoredFile(docRecord.getStoragePath());
            throw new RuntimeException("文档处理失败: " + e.getMessage(), e);
        }
    }

    public String uploadDocument(MultipartFile file) {
        return uploadDocument(file, null);
    }

    public List<Document> searchFromKnowledgeBase(String query, UUID kbId, int topK) {
        SearchRequest request = SearchRequest.builder()
                .query(query)
                .topK(topK)
                .similarityThreshold(0.6)
                .build();
        if (kbId != null) {
            request = SearchRequest.from(request)
                    .filterExpression("kb_id == '" + kbId + "'")
                    .build();
        }
        return vectorStore.similaritySearch(request);
    }

    public List<KnowledgeBaseDocument> getDocumentList(UUID kbId) {
        if (kbId != null) {
            return documentMapper.selectList(new LambdaQueryWrapper<KnowledgeBaseDocument>()
                    .eq(KnowledgeBaseDocument::getKbId, kbId)
                    .orderByDesc(KnowledgeBaseDocument::getCreatedAt));
        }
        return documentMapper.selectList(new LambdaQueryWrapper<KnowledgeBaseDocument>()
                .orderByDesc(KnowledgeBaseDocument::getCreatedAt));
    }

    public List<Map<String, Object>> previewDocument(UUID docId) {
        KnowledgeBaseDocument document = documentMapper.selectById(docId);
        if (document == null) {
            throw new RuntimeException("文档记录不存在: " + docId);
        }
        if (document.getStoragePath() == null || document.getStoragePath().isBlank()) {
            throw new RuntimeException("当前文档没有可预览的原始文件");
        }

        Path storedFile = Path.of(document.getStoragePath());
        if (!Files.exists(storedFile)) {
            throw new RuntimeException("原始文件不存在，无法预览");
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

    public void deleteDocument(UUID docId) {
        KnowledgeBaseDocument document = documentMapper.selectById(docId);
        if (document == null) {
            throw new RuntimeException("文档记录不存在: " + docId);
        }
        vectorStoreMapper.deleteByDocumentId(docId.toString());
        deleteStoredFile(document.getStoragePath());
        documentMapper.deleteById(docId);
    }

    public void deleteKnowledgeBaseVectors(UUID kbId) {
        vectorStoreMapper.deleteByKnowledgeBaseId(kbId.toString());
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

    private Path saveUploadedFile(MultipartFile file) {
        try {
            Files.createDirectories(STORAGE_ROOT);
            String originalName = file.getOriginalFilename() == null ? "document" : file.getOriginalFilename();
            String storedName = UUID.randomUUID() + "_" + originalName.replaceAll("[\\\\/:*?\"<>|]", "_");
            Path target = STORAGE_ROOT.resolve(storedName);
            Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
            return target;
        } catch (IOException e) {
            throw new RuntimeException("保存上传文档失败: " + e.getMessage(), e);
        }
    }

    private List<Document> readDocuments(Path filePath) {
        TikaDocumentReader reader = new TikaDocumentReader(new org.springframework.core.io.FileSystemResource(filePath));
        return reader.read();
    }
}
