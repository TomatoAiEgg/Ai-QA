package cn.net.tomatoegg.ai.service;

import cn.net.tomatoegg.ai.entity.KnowledgeBaseDocument;
import cn.net.tomatoegg.ai.repository.KnowledgeBaseDocumentRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 知识库服务
 * 负责文档的上传、解析、向量化和存储
 *
 * @author 苏三
 */
@Slf4j
@Service
public class KnowledgeBaseService {

    private final VectorStore vectorStore;
    private final JdbcTemplate jdbcTemplate;
    private final TokenTextSplitter tokenTextSplitter;
    private final KnowledgeBaseDocumentRepository documentRepository;

    public KnowledgeBaseService(VectorStore vectorStore, 
                                JdbcTemplate jdbcTemplate, 
                                TokenTextSplitter tokenTextSplitter,
                                KnowledgeBaseDocumentRepository documentRepository) {
        this.vectorStore = vectorStore;
        this.jdbcTemplate = jdbcTemplate;
        this.tokenTextSplitter = tokenTextSplitter;
        this.documentRepository = documentRepository;
    }

    /**
     * 上传并处理文档到指定知识库
     *
     * @param file 上传的文件
     * @param kbId 知识库 ID（可选）
     * @return 处理结果
     */
    public String uploadDocument(MultipartFile file, UUID kbId) {
        // 创建文档记录
        KnowledgeBaseDocument docRecord = KnowledgeBaseDocument.builder()
                .id(UUID.randomUUID())
                .kbId(kbId)
                .filename(file.getOriginalFilename())
                .fileSize(file.getSize())
                .fileHash(calculateFileHash(file))
                .chunkCount(0)
                .status("PROCESSING")
                .build();

        documentRepository.create(docRecord);
        log.info("创建文档记录：{} (kbId: {})", docRecord.getFilename(), kbId);

        try {
            // 1. 保存文件到临时目录
            Path tempFile = Files.createTempFile("kb_", file.getOriginalFilename());
            Files.copy(file.getInputStream(), tempFile, StandardCopyOption.REPLACE_EXISTING);

            // 2. 使用 Tika 读取文档内容
            TikaDocumentReader reader = new TikaDocumentReader(new org.springframework.core.io.FileSystemResource(tempFile));
            List<Document> documents = reader.read();

            // 统计原始文档信息
            int originalDocCount = documents.size();
            int totalOriginalChars = documents.stream()
                    .mapToInt(doc -> doc.getContent().length())
                    .sum();
            log.info("文档解析完成：{} 个文档，总字符数：{}", originalDocCount, totalOriginalChars);

            // 3. 文本切分 (Splitter)
            List<Document> splitDocuments = tokenTextSplitter.apply(documents);

            // 4. 添加元数据
            for (int i = 0; i < splitDocuments.size(); i++) {
                Document doc = splitDocuments.get(i);
                doc.getMetadata().put("filename", file.getOriginalFilename());
                doc.getMetadata().put("uploadTime", System.currentTimeMillis());
                doc.getMetadata().put("kb_id", kbId != null ? kbId.toString() : "default");
                doc.getMetadata().put("chunkIndex", i);
                doc.getMetadata().put("totalChunks", splitDocuments.size());
            }

            // 5. 统计切片信息
            Map<String, Object> stats = analyzeChunks(splitDocuments, totalOriginalChars);
            log.info("文档切片统计：{}", stats);

            // 6. 存入向量数据库
            vectorStore.add(splitDocuments);

            // 7. 更新文档记录
            documentRepository.updateChunkCount(docRecord.getId(), splitDocuments.size());
            documentRepository.updateStatus(docRecord.getId(), "COMPLETED", null);

            // 8. 清理临时文件
            Files.deleteIfExists(tempFile);

            log.info("成功处理文档：{}, 生成片段数：{}", file.getOriginalFilename(), splitDocuments.size());
            return String.format("文档上传并处理成功，共生成 %d 个片段 (平均每个片段 %d 字符，重叠率 %.1f%%)",
                    splitDocuments.size(),
                    stats.get("avgChunkSize"),
                    stats.get("overlapRatio"));

        } catch (IOException e) {
            log.error("文档处理失败", e);
            documentRepository.updateStatus(docRecord.getId(), "FAILED", e.getMessage());
            throw new RuntimeException("文档处理失败：" + e.getMessage());
        }
    }

    /**
     * 上传并处理文档（默认不指定知识库）
     */
    public String uploadDocument(MultipartFile file) {
        return uploadDocument(file, null);
    }

    /**
     * 从指定知识库检索文档
     *
     * @param query 查询问题
     * @param kbId 知识库 ID（可选，为 null 时检索所有知识库）
     * @param topK 返回文档数量
     * @return 检索到的文档列表
     */
    public List<Document> searchFromKnowledgeBase(String query, UUID kbId, int topK) {
        SearchRequest request = SearchRequest.builder()
                .query(query)
                .topK(topK)
                .similarityThreshold(0.6)
                .build();

        return vectorStore.similaritySearch(request);
    }

    /**
     * 获取知识库中的文档列表
     *
     * @param kbId 知识库 ID（可选）
     * @return 文档列表
     */
    public List<KnowledgeBaseDocument> getDocumentList(UUID kbId) {
        if (kbId != null) {
            return documentRepository.findByKbId(kbId);
        }
        return documentRepository.findAll();
    }

    /**
     * 删除知识库中的文档
     *
     * @param docId 文档记录 ID
     */
    public void deleteDocument(UUID docId) {
        KnowledgeBaseDocument doc = documentRepository.findById(docId)
                .orElseThrow(() -> new RuntimeException("文档记录不存在：" + docId));

        // 从向量库中删除相关片段（通过元数据过滤）
        // 注意：Spring AI VectorStore 不直接支持删除，需要通过 JdbcTemplate 操作
        String deleteSql = "DELETE FROM vector_store_1024 WHERE metadata->>'filename' = ?";
        jdbcTemplate.update(deleteSql, doc.getFilename());

        // 删除文档记录
        documentRepository.delete(docId);
        log.info("删除文档：{} (kbId: {})", doc.getFilename(), doc.getKbId());
    }

    /**
     * 分析切片统计信息
     */
    private Map<String, Object> analyzeChunks(List<Document> chunks, int totalOriginalChars) {
        Map<String, Object> stats = new HashMap<>();

        if (chunks.isEmpty()) {
            stats.put("avgChunkSize", 0);
            stats.put("minChunkSize", 0);
            stats.put("maxChunkSize", 0);
            stats.put("overlapRatio", 0.0);
            return stats;
        }

        List<Integer> chunkSizes = chunks.stream()
                .map(doc -> doc.getContent().length())
                .toList();

        int avgSize = chunkSizes.stream().mapToInt(Integer::intValue).sum() / chunkSizes.size();
        int minSize = chunkSizes.stream().min(Integer::compareTo).orElse(0);
        int maxSize = chunkSizes.stream().max(Integer::compareTo).orElse(0);

        int totalChunkChars = chunkSizes.stream().mapToInt(Integer::intValue).sum();
        double overlapRatio = totalOriginalChars > 0 ?
                ((double) totalChunkChars / totalOriginalChars - 1) * 100 : 0;

        stats.put("avgChunkSize", avgSize);
        stats.put("minChunkSize", minSize);
        stats.put("maxChunkSize", maxSize);
        stats.put("overlapRatio", Math.round(overlapRatio * 10.0) / 10.0);
        stats.put("totalChunkChars", totalChunkChars);

        return stats;
    }

    /**
     * 计算文件哈希值（用于去重）
     */
    private String calculateFileHash(MultipartFile file) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(file.getBytes());
            StringBuilder hexString = new StringBuilder(2 * hash.length);
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            log.warn("计算文件哈希失败", e);
            return null;
        }
    }
}
