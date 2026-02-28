package cn.net.susan.ai.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.core.io.Resource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
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

    public KnowledgeBaseService(VectorStore vectorStore, JdbcTemplate jdbcTemplate) {
        this.vectorStore = vectorStore;
        this.jdbcTemplate = jdbcTemplate;
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

            // 3. 文本切分 (Splitter)
            TokenTextSplitter splitter = new TokenTextSplitter();
            List<Document> splitDocuments = splitter.apply(documents);

            // 4. 添加元数据 (可选)
            for (Document doc : splitDocuments) {
                doc.getMetadata().put("filename", file.getOriginalFilename());
                doc.getMetadata().put("uploadTime", System.currentTimeMillis());
            }

            // 5. 存入向量数据库
            vectorStore.add(splitDocuments);

            // 6. 清理临时文件
            Files.deleteIfExists(tempFile);

            log.info("成功处理文档: {}, 生成片段数: {}", file.getOriginalFilename(), splitDocuments.size());
            return "文档上传并处理成功，共生成 " + splitDocuments.size() + " 个片段";

        } catch (IOException e) {
            log.error("文档处理失败", e);
            throw new RuntimeException("文档处理失败: " + e.getMessage());
        }
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
            log.warn("查询文档列表失败，可能是表不存在: {}", e.getMessage());
            return List.of();
        }
    }
}
