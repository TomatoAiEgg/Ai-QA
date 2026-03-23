package cn.net.tomatoegg.ai.repository;

import cn.net.tomatoegg.ai.entity.KnowledgeBase;
import jakarta.annotation.PostConstruct;
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
 * 知识库数据访问层
 *
 * @author 苏三
 * @date 2026/3/17
 */
@Repository
@RequiredArgsConstructor
public class KnowledgeBaseRepository {

    private final JdbcTemplate jdbcTemplate;

    @PostConstruct
    void initSchema() {
        jdbcTemplate.execute(
            "CREATE TABLE IF NOT EXISTS knowledge_bases (" +
            "  id UUID PRIMARY KEY," +
            "  name VARCHAR(255) NOT NULL," +
            "  description TEXT," +
            "  cover_color VARCHAR(32)," +
            "  created_by UUID," +
            "  is_public BOOLEAN NOT NULL DEFAULT FALSE," +
            "  created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()," +
            "  updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()" +
            ")"
        );
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_knowledge_bases_created_at ON knowledge_bases(created_at DESC)");
    }

    /**
     * 创建知识库
     */
    public KnowledgeBase create(KnowledgeBase kb) {
        String sql = "INSERT INTO knowledge_bases (id, name, description, cover_color, created_by, is_public) " +
                     "VALUES (?, ?, ?, ?, ?, ?)";

        jdbcTemplate.update(sql,
            kb.getId(),
            kb.getName(),
            kb.getDescription(),
            kb.getCoverColor(),
            kb.getCreatedBy(),
            kb.getIsPublic()
        );

        return kb;
    }

    /**
     * 获取所有知识库
     */
    public List<KnowledgeBase> findAll() {
        String sql = "SELECT id, name, description, cover_color, created_by, is_public, created_at, updated_at " +
                     "FROM knowledge_bases " +
                     "ORDER BY created_at DESC";
        return jdbcTemplate.query(sql, knowledgeBaseRowMapper);
    }

    /**
     * 根据 ID 获取知识库
     */
    public Optional<KnowledgeBase> findById(UUID id) {
        String sql = "SELECT id, name, description, cover_color, created_by, is_public, created_at, updated_at " +
                     "FROM knowledge_bases " +
                     "WHERE id = ?";

        try {
            KnowledgeBase kb = jdbcTemplate.queryForObject(sql, knowledgeBaseRowMapper, id);
            return Optional.ofNullable(kb);
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    /**
     * 更新知识库
     */
    public void update(KnowledgeBase kb) {
        String sql = "UPDATE knowledge_bases " +
                     "SET name = ?, description = ?, cover_color = ?, is_public = ?, updated_at = NOW() " +
                     "WHERE id = ?";
        jdbcTemplate.update(sql,
            kb.getName(),
            kb.getDescription(),
            kb.getCoverColor(),
            kb.getIsPublic(),
            kb.getId()
        );
    }

    /**
     * 删除知识库
     */
    public void delete(UUID id) {
        String sql = "DELETE FROM knowledge_bases WHERE id = ?";
        jdbcTemplate.update(sql, id);
    }

    private static final RowMapper<KnowledgeBase> knowledgeBaseRowMapper = new RowMapper<>() {
        @Override
        public KnowledgeBase mapRow(ResultSet rs, int rowNum) throws SQLException {
            return KnowledgeBase.builder()
                .id((UUID) rs.getObject("id"))
                .name(rs.getString("name"))
                .description(rs.getString("description"))
                .coverColor(rs.getString("cover_color"))
                .createdBy((UUID) rs.getObject("created_by"))
                .isPublic(rs.getBoolean("is_public"))
                .createdAt(rs.getObject("created_at", OffsetDateTime.class))
                .updatedAt(rs.getObject("updated_at", OffsetDateTime.class))
                .build();
        }
    };
}
