package cn.net.tomatoegg.ai.service.conversation;

import cn.net.tomatoegg.ai.common.ApiCode;
import cn.net.tomatoegg.ai.entity.Conversation;
import cn.net.tomatoegg.ai.entity.ConversationMessage;
import cn.net.tomatoegg.ai.exception.BusinessException;
import cn.net.tomatoegg.ai.mapper.ConversationMapper;
import cn.net.tomatoegg.ai.mapper.ConversationMessageMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
public class ConversationService {

    private static final String DEFAULT_CONVERSATION_TITLE = "新的对话";
    private static final List<String> PLACEHOLDER_TITLES = List.of(
            DEFAULT_CONVERSATION_TITLE,
            "New Chat"
    );

    private final ConversationMapper conversationMapper;
    private final ConversationMessageMapper messageMapper;

    public ConversationService(ConversationMapper conversationMapper, ConversationMessageMapper messageMapper) {
        this.conversationMapper = conversationMapper;
        this.messageMapper = messageMapper;
    }

    @Caching(evict = {
            @CacheEvict(value = "conversationListCache", key = "#userId.toString() + ':v2'"),
            @CacheEvict(value = "conversationMessagesCache", allEntries = true)
    })
    public UUID createConversation(UUID userId, String title) {
        UUID id = UUID.randomUUID();
        conversationMapper.insert(Conversation.builder()
                .id(id)
                .userId(userId)
                .title(normalizeTitle(title))
                .build());
        return id;
    }

    @Caching(evict = {
            @CacheEvict(value = "conversationListCache", key = "#userId.toString() + ':v2'"),
            @CacheEvict(value = "conversationMessagesCache", allEntries = true)
    })
    public void renameConversation(UUID userId, UUID id, String title) {
        requireOwnedConversation(userId, id);
        conversationMapper.update(null, new LambdaUpdateWrapper<Conversation>()
                .eq(Conversation::getId, id)
                .eq(Conversation::getUserId, userId)
                .set(Conversation::getTitle, normalizeTitle(title))
                .setSql("updated_at = NOW()"));
    }

    @Caching(evict = {
            @CacheEvict(value = "conversationListCache", key = "#userId.toString() + ':v2'"),
            @CacheEvict(value = "conversationMessagesCache", allEntries = true)
    })
    public void deleteConversation(UUID userId, UUID id) {
        requireOwnedConversation(userId, id);
        conversationMapper.delete(new LambdaQueryWrapper<Conversation>()
                .eq(Conversation::getId, id)
                .eq(Conversation::getUserId, userId));
    }

    @Cacheable(value = "conversationListCache", key = "#userId.toString() + ':v2'")
    public List<Conversation> listConversations(UUID userId) {
        List<Conversation> conversations = conversationMapper.selectList(new LambdaQueryWrapper<Conversation>()
                .eq(Conversation::getUserId, userId)
                .orderByDesc(Conversation::getUpdatedAt));
        repairConversationTitles(userId, conversations);
        return conversations;
    }

    @Caching(evict = {
            @CacheEvict(value = "conversationListCache", key = "#userId.toString() + ':v2'"),
            @CacheEvict(value = "conversationMessagesCache", allEntries = true)
    })
    public void addUserMessage(UUID userId, UUID conversationId, String content) {
        addMessage(userId, conversationId, "USER", content);
        String conversationTitle = buildConversationTitle(content);
        conversationMapper.update(null, new LambdaUpdateWrapper<Conversation>()
                .eq(Conversation::getId, conversationId)
                .eq(Conversation::getUserId, userId)
                .and(wrapper -> wrapper.isNull(Conversation::getTitle)
                        .or().eq(Conversation::getTitle, "")
                        .or().in(Conversation::getTitle, PLACEHOLDER_TITLES))
                .set(Conversation::getTitle, conversationTitle)
                .setSql("updated_at = NOW()"));
    }

    @Caching(evict = {
            @CacheEvict(value = "conversationListCache", key = "#userId.toString() + ':v2'"),
            @CacheEvict(value = "conversationMessagesCache", allEntries = true)
    })
    public void addAssistantMessage(UUID userId, UUID conversationId, String content) {
        addMessage(userId, conversationId, "ASSISTANT", content);
    }

    @Cacheable(value = "conversationMessagesCache", key = "#userId.toString() + ':' + #conversationId.toString() + ':list:' + #limit")
    public List<ConversationMessage> listMessages(UUID userId, UUID conversationId, int limit) {
        requireOwnedConversation(userId, conversationId);
        return messageMapper.selectList(new LambdaQueryWrapper<ConversationMessage>()
                .eq(ConversationMessage::getConversationId, conversationId)
                .orderByAsc(ConversationMessage::getCreatedAt)
                .last("LIMIT " + limit));
    }

    @Cacheable(value = "conversationMessagesCache", key = "#userId.toString() + ':' + #conversationId.toString() + ':recent:' + #limit")
    public List<ConversationMessage> listRecentMessages(UUID userId, UUID conversationId, int limit) {
        requireOwnedConversation(userId, conversationId);
        List<ConversationMessage> items = messageMapper.selectList(new LambdaQueryWrapper<ConversationMessage>()
                .eq(ConversationMessage::getConversationId, conversationId)
                .orderByDesc(ConversationMessage::getCreatedAt)
                .last("LIMIT " + limit));
        List<ConversationMessage> ordered = new ArrayList<>(items);
        Collections.reverse(ordered);
        return ordered;
    }

    private void repairConversationTitles(UUID userId, List<Conversation> conversations) {
        for (Conversation conversation : conversations) {
            String normalizedTitle = conversation.getTitle() == null ? null : conversation.getTitle().strip();
            if (!hasText(normalizedTitle)) {
                normalizedTitle = DEFAULT_CONVERSATION_TITLE;
            }

            if (isPlaceholderTitle(normalizedTitle)) {
                normalizedTitle = resolveTitleFromFirstUserMessage(conversation.getId());
            }

            if (!Objects.equals(conversation.getTitle(), normalizedTitle)) {
                conversation.setTitle(normalizedTitle);
                conversationMapper.update(null, new LambdaUpdateWrapper<Conversation>()
                        .eq(Conversation::getId, conversation.getId())
                        .eq(Conversation::getUserId, userId)
                        .set(Conversation::getTitle, normalizedTitle));
            }
        }
    }

    private void addMessage(UUID userId, UUID conversationId, String role, String content) {
        requireOwnedConversation(userId, conversationId);
        messageMapper.insert(ConversationMessage.builder()
                .id(UUID.randomUUID())
                .conversationId(conversationId)
                .role(role)
                .content(content)
                .build());
        conversationMapper.update(null, new LambdaUpdateWrapper<Conversation>()
                .eq(Conversation::getId, conversationId)
                .eq(Conversation::getUserId, userId)
                .setSql("updated_at = NOW()"));
    }

    private Conversation requireOwnedConversation(UUID userId, UUID conversationId) {
        Conversation conversation = conversationMapper.selectOne(new LambdaQueryWrapper<Conversation>()
                .eq(Conversation::getId, conversationId)
                .eq(Conversation::getUserId, userId)
                .last("LIMIT 1"));
        if (conversation == null) {
            throw new BusinessException(ApiCode.NOT_FOUND, "对话不存在或无权访问");
        }
        return conversation;
    }

    private String buildConversationTitle(String content) {
        if (!hasText(content)) {
            return DEFAULT_CONVERSATION_TITLE;
        }
        String normalized = content.strip();
        int lineBreakIndex = normalized.indexOf('\n');
        if (lineBreakIndex >= 0) {
            normalized = normalized.substring(0, lineBreakIndex).strip();
        }
        return normalized.isEmpty() ? DEFAULT_CONVERSATION_TITLE : normalized;
    }

    private String resolveTitleFromFirstUserMessage(UUID conversationId) {
        ConversationMessage firstUserMessage = messageMapper.selectOne(new LambdaQueryWrapper<ConversationMessage>()
                .eq(ConversationMessage::getConversationId, conversationId)
                .eq(ConversationMessage::getRole, "USER")
                .orderByAsc(ConversationMessage::getCreatedAt)
                .last("LIMIT 1"));
        if (firstUserMessage == null) {
            return DEFAULT_CONVERSATION_TITLE;
        }
        return buildConversationTitle(firstUserMessage.getContent());
    }

    private String normalizeTitle(String title) {
        String normalizedTitle = title == null ? null : title.strip();
        return hasText(normalizedTitle) ? normalizedTitle : DEFAULT_CONVERSATION_TITLE;
    }

    private boolean isPlaceholderTitle(String title) {
        return !hasText(title) || PLACEHOLDER_TITLES.contains(title);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
