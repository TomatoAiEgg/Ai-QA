package cn.net.susan.ai.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

    public KnowledgeBaseService(VectorStore vectorStore, JdbcTemplate jdbcTemplate, TokenTextSplitter tokenTextSplitter) {
        this.vectorStore = vectorStore;
        this.jdbcTemplate = jdbcTemplate;
        this.tokenTextSplitter = tokenTextSplitter;
    }

    /**
     * 上传并处理文档
     *
     * @param file 上传的文件
     * @return 处理结果
     */
    public String uploadDocument(MultipartFile file) {
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
                // 添加切片索引，方便追踪
                doc.getMetadata().put("chunkIndex", i);
                doc.getMetadata().put("totalChunks", splitDocuments.size());
            }

            // 5. 统计切片信息
            Map<String, Object> stats = analyzeChunks(splitDocuments, totalOriginalChars);
            log.info("文档切片统计：{}", stats);

            // 6. 存入向量数据库
            vectorStore.add(splitDocuments);

            // 7. 清理临时文件
            Files.deleteIfExists(tempFile);

            log.info("成功处理文档：{}, 生成片段数：{}", file.getOriginalFilename(), splitDocuments.size());
            return String.format("文档上传并处理成功，共生成 %d 个片段 (平均每个片段 %d 字符，重叠率 %.1f%%)",
                    splitDocuments.size(),
                    stats.get("avgChunkSize"),
                    stats.get("overlapRatio"));

        } catch (IOException e) {
            log.error("文档处理失败", e);
            throw new RuntimeException("文档处理失败：" + e.getMessage());
        }
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

        // 计算每个片段的大小
        List<Integer> chunkSizes = chunks.stream()
                .map(doc -> doc.getContent().length())
                .toList();

        int avgSize = chunkSizes.stream().mapToInt(Integer::intValue).sum() / chunkSizes.size();
        int minSize = chunkSizes.stream().min(Integer::compareTo).orElse(0);
        int maxSize = chunkSizes.stream().max(Integer::compareTo).orElse(0);

        // 计算重叠率 (总片段字符数 / 原始字符数 - 1)
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
     * 获取知识库中的文档列表（基于文件名去重）
     *
     * @return 文档列表
     */
    public List<Map<String, Object>> getDocumentList() {
        String sql = "SELECT metadata->>'filename' as filename, " +
                "COUNT(*) as chunk_count, " +
                "MIN(metadata->>'uploadTime') as upload_time " +
                "FROM vector_store_768 " +
                "GROUP BY metadata->>'filename' " +
                "ORDER BY upload_time DESC";

        try {
            return jdbcTemplate.queryForList(sql);
        } catch (Exception e) {
            log.warn("查询文档列表失败，可能是表不存在：{}", e.getMessage());
            return List.of();
        }
    }
}
