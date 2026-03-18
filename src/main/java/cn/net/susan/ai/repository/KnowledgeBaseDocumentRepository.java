package cn.net.susan.ai.repository;

import cn.net.susan.ai.entity.KnowledgeBaseDocument;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 知识库文档关联数据访问层
 *
 * @author 苏三
 * @date 2026/3/17
 */
@Repository
@RequiredArgsConstructor
public class KnowledgeBaseDocumentRepository {

    private final JdbcTemplate jdbcTemplate;

    /**
     * 创建文档关联记录
     */
    public KnowledgeBaseDocument create(KnowledgeBaseDocument doc) {
        String sql = "INSERT INTO knowledge_base_documents " +
                     "(id, kb_id, filename, file_hash, file_size, chunk_count, status) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?)";

        jdbcTemplate.update(sql,
            doc.getId(),
            doc.getKbId(),
            doc.getFilename(),
            doc.getFileHash(),
            doc.getFileSize(),
            doc.getChunkCount(),
            doc.getStatus()
        );

        return doc;
    }

    /**
     * 根据 ID 获取文档记录
     */
    public Optional<KnowledgeBaseDocument> findById(UUID id) {
        String sql = "SELECT id, kb_id, filename, file_hash, file_size, chunk_count, " +
                     "status, error_message, created_at, updated_at " +
                     "FROM knowledge_base_documents " +
                     "WHERE id = ?";

        try {
            KnowledgeBaseDocument doc = jdbcTemplate.queryForObject(sql, documentRowMapper, id);
            return Optional.ofNullable(doc);
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    /**
     * 根据知识库 ID 获取文档列表
     */
    public List<KnowledgeBaseDocument> findByKbId(UUID kbId) {
        String sql = "SELECT id, kb_id, filename, file_hash, file_size, chunk_count, " +
                     "status, error_message, created_at, updated_at " +
                     "FROM knowledge_base_documents " +
                     "WHERE kb_id = ? " +
                     "ORDER BY created_at DESC";

        return jdbcTemplate.query(sql, documentRowMapper, kbId);
    }

    /**
     * 获取所有文档列表
     */
    public List<KnowledgeBaseDocument> findAll() {
        String sql = "SELECT id, kb_id, filename, file_hash, file_size, chunk_count, " +
                     "status, error_message, created_at, updated_at " +
                     "FROM knowledge_base_documents " +
                     "ORDER BY created_at DESC";

        return jdbcTemplate.query(sql, documentRowMapper);
    }

    /**
     * 更新文档状态
     */
    public void updateStatus(UUID id, String status, String errorMessage) {
        String sql = "UPDATE knowledge_base_documents " +
                     "SET status = ?, error_message = ?, updated_at = NOW() " +
                     "WHERE id = ?";

        jdbcTemplate.update(sql, status, errorMessage, id);
    }

    /**
     * 更新切片数量
     */
    public void updateChunkCount(UUID id, int chunkCount) {
        String sql = "UPDATE knowledge_base_documents " +
                     "SET chunk_count = ?, updated_at = NOW() " +
                     "WHERE id = ?";

        jdbcTemplate.update(sql, chunkCount, id);
    }

    /**
     * 删除文档记录
     */
    public void delete(UUID id) {
        String sql = "DELETE FROM knowledge_base_documents WHERE id = ?";
        jdbcTemplate.update(sql, id);
    }

    /**
     * 删除知识库下所有文档记录
     */
    public void deleteByKbId(UUID kbId) {
        String sql = "DELETE FROM knowledge_base_documents WHERE kb_id = ?";
        jdbcTemplate.update(sql, kbId);
    }

    private static final RowMapper<KnowledgeBaseDocument> documentRowMapper = new RowMapper<KnowledgeBaseDocument>() {
        @Override
        public KnowledgeBaseDocument mapRow(ResultSet rs, int rowNum) throws SQLException {
            return KnowledgeBaseDocument.builder()
                .id((UUID) rs.getObject("id"))
                .kbId((UUID) rs.getObject("kb_id"))
                .filename(rs.getString("filename"))
                .fileHash(rs.getString("file_hash"))
                .fileSize(rs.getLong("file_size"))
                .chunkCount(rs.getInt("chunk_count"))
                .status(rs.getString("status"))
                .errorMessage(rs.getString("error_message"))
                .createdAt(rs.getObject("created_at", OffsetDateTime.class))
                .updatedAt(rs.getObject("updated_at", OffsetDateTime.class))
                .build();
        }
    };
}
