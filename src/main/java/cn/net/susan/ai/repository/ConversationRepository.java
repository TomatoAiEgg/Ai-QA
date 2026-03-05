package cn.net.susan.ai.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public class ConversationRepository {

    private final JdbcTemplate jdbcTemplate;

    public ConversationRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        initSchema();
    }

    private void initSchema() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS conversations (
                  id UUID PRIMARY KEY,
                  title VARCHAR(255) NOT NULL,
                  created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
                  updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS conversation_messages (
                  id UUID PRIMARY KEY,
                  conversation_id UUID NOT NULL REFERENCES conversations(id) ON DELETE CASCADE,
                  role VARCHAR(20) NOT NULL,
                  content TEXT NOT NULL,
                  created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
                )
                """);
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_conversation_messages_conv_time ON conversation_messages(conversation_id, created_at)");
    }

    public UUID createConversation(String title) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO conversations(id, title) VALUES (?, ?)", id, title);
        return id;
    }

    public void updateConversationTitle(UUID id, String title) {
        jdbcTemplate.update("UPDATE conversations SET title = ?, updated_at = NOW() WHERE id = ?", title, id);
    }

    public List<ConversationRow> listConversations() {
        return jdbcTemplate.query("""
                SELECT id, title, created_at, updated_at
                FROM conversations
                ORDER BY updated_at DESC
                """, conversationRowMapper);
    }

    public void addMessage(UUID conversationId, String role, String content) {
        jdbcTemplate.update("INSERT INTO conversation_messages(id, conversation_id, role, content) VALUES (?, ?, ?, ?)",
                UUID.randomUUID(), conversationId, role, content);
        jdbcTemplate.update("UPDATE conversations SET updated_at = NOW() WHERE id = ?", conversationId);
    }

    public void updateTitleIfDefault(UUID id, String newTitle) {
        jdbcTemplate.update("""
                UPDATE conversations
                SET title = ?, updated_at = NOW()
                WHERE id = ? AND (title IS NULL OR title = '' OR title = '新的对话' OR title = 'New Chat')
                """, newTitle, id);
    }

    public void deleteConversation(UUID id) {
        jdbcTemplate.update("DELETE FROM conversations WHERE id = ?", id);
    }

    public List<MessageRow> listMessages(UUID conversationId, int limit) {
        return jdbcTemplate.query("""
                SELECT id, conversation_id, role, content, created_at
                FROM conversation_messages
                WHERE conversation_id = ?
                ORDER BY created_at ASC
                LIMIT ?
                """, messageRowMapper, conversationId, limit);
    }

    public List<MessageRow> listRecentMessages(UUID conversationId, int limit) {
        List<MessageRow> items = jdbcTemplate.query("""
                SELECT id, conversation_id, role, content, created_at
                FROM conversation_messages
                WHERE conversation_id = ?
                ORDER BY created_at DESC
                LIMIT ?
                """, messageRowMapper, conversationId, limit);
        java.util.ArrayList<MessageRow> copy = new java.util.ArrayList<>(items);
        java.util.Collections.reverse(copy);
        return copy;
    }

    public record ConversationRow(UUID id, String title, OffsetDateTime createdAt, OffsetDateTime updatedAt) {}

    public record MessageRow(UUID id, UUID conversationId, String role, String content, OffsetDateTime createdAt) {}

    private final RowMapper<ConversationRow> conversationRowMapper = new RowMapper<>() {
        @Override
        public ConversationRow mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new ConversationRow(
                    (UUID) rs.getObject("id"),
                    rs.getString("title"),
                    rs.getObject("created_at", OffsetDateTime.class),
                    rs.getObject("updated_at", OffsetDateTime.class)
            );
        }
    };

    private final RowMapper<MessageRow> messageRowMapper = new RowMapper<>() {
        @Override
        public MessageRow mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new MessageRow(
                    (UUID) rs.getObject("id"),
                    (UUID) rs.getObject("conversation_id"),
                    rs.getString("role"),
                    rs.getString("content"),
                    rs.getObject("created_at", OffsetDateTime.class)
            );
        }
    };
}
