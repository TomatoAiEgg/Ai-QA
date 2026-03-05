package cn.net.susan.ai.service;

import cn.net.susan.ai.repository.ConversationRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class ConversationService {

    private final ConversationRepository conversationRepository;

    public ConversationService(ConversationRepository conversationRepository) {
        this.conversationRepository = conversationRepository;
    }

    public UUID createConversation(String title) {
        return conversationRepository.createConversation(title);
    }

    public void renameConversation(UUID id, String title) {
        conversationRepository.updateConversationTitle(id, title);
    }

    public void deleteConversation(UUID id) {
        conversationRepository.deleteConversation(id);
    }

    public List<ConversationRepository.ConversationRow> listConversations() {
        return conversationRepository.listConversations();
    }

    public void addUserMessage(UUID conversationId, String content) {
        conversationRepository.addMessage(conversationId, "USER", content);
        conversationRepository.updateTitleIfDefault(conversationId, content);
    }

    public void addAssistantMessage(UUID conversationId, String content) {
        conversationRepository.addMessage(conversationId, "ASSISTANT", content);
    }

    public List<ConversationRepository.MessageRow> listMessages(UUID conversationId, int limit) {
        return conversationRepository.listMessages(conversationId, limit);
    }

    public List<ConversationRepository.MessageRow> listRecentMessages(UUID conversationId, int limit) {
        return conversationRepository.listRecentMessages(conversationId, limit);
    }
}
