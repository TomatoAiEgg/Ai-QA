package cn.net.tomatoegg.ai.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import cn.net.tomatoegg.ai.entity.Conversation;
import cn.net.tomatoegg.ai.entity.ConversationMessage;
import cn.net.tomatoegg.ai.mapper.ConversationMapper;
import cn.net.tomatoegg.ai.mapper.ConversationMessageMapper;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Service
public class ConversationService {

    private final ConversationMapper conversationMapper;
    private final ConversationMessageMapper messageMapper;

    public ConversationService(ConversationMapper conversationMapper, ConversationMessageMapper messageMapper) {
        this.conversationMapper = conversationMapper;
        this.messageMapper = messageMapper;
    }

    public UUID createConversation(String title) {
        UUID id = UUID.randomUUID();
        conversationMapper.insert(Conversation.builder().id(id).title(title).build());
        return id;
    }

    public void renameConversation(UUID id, String title) {
        conversationMapper.update(null, new LambdaUpdateWrapper<Conversation>()
                .eq(Conversation::getId, id)
                .set(Conversation::getTitle, title)
                .setSql("updated_at = NOW()"));
    }

    public void deleteConversation(UUID id) {
        conversationMapper.deleteById(id);
    }

    public List<Conversation> listConversations() {
        return conversationMapper.selectList(new LambdaQueryWrapper<Conversation>()
                .orderByDesc(Conversation::getUpdatedAt));
    }

    public void addUserMessage(UUID conversationId, String content) {
        addMessage(conversationId, "USER", content);
        conversationMapper.update(null, new LambdaUpdateWrapper<Conversation>()
                .eq(Conversation::getId, conversationId)
                .and(wrapper -> wrapper.isNull(Conversation::getTitle)
                        .or().eq(Conversation::getTitle, "")
                        .or().eq(Conversation::getTitle, "新的对话")
                        .or().eq(Conversation::getTitle, "New Chat"))
                .set(Conversation::getTitle, content)
                .setSql("updated_at = NOW()"));
    }

    public void addAssistantMessage(UUID conversationId, String content) {
        addMessage(conversationId, "ASSISTANT", content);
    }

    public List<ConversationMessage> listMessages(UUID conversationId, int limit) {
        return messageMapper.selectList(new LambdaQueryWrapper<ConversationMessage>()
                .eq(ConversationMessage::getConversationId, conversationId)
                .orderByAsc(ConversationMessage::getCreatedAt)
                .last("LIMIT " + limit));
    }

    public List<ConversationMessage> listRecentMessages(UUID conversationId, int limit) {
        List<ConversationMessage> items = messageMapper.selectList(new LambdaQueryWrapper<ConversationMessage>()
                .eq(ConversationMessage::getConversationId, conversationId)
                .orderByDesc(ConversationMessage::getCreatedAt)
                .last("LIMIT " + limit));
        List<ConversationMessage> ordered = new ArrayList<>(items);
        Collections.reverse(ordered);
        return ordered;
    }

    private void addMessage(UUID conversationId, String role, String content) {
        messageMapper.insert(ConversationMessage.builder()
                .id(UUID.randomUUID())
                .conversationId(conversationId)
                .role(role)
                .content(content)
                .build());
        conversationMapper.update(null, new LambdaUpdateWrapper<Conversation>()
                .eq(Conversation::getId, conversationId)
                .setSql("updated_at = NOW()"));
    }
}
