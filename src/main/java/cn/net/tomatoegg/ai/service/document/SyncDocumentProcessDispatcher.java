package cn.net.tomatoegg.ai.service.document;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@ConditionalOnProperty(prefix = "ai.service.rocketmq.document-process", name = "enabled", havingValue = "false", matchIfMissing = true)
public class SyncDocumentProcessDispatcher implements DocumentProcessDispatcher {

    private final DocumentProcessingService documentProcessingService;

    public SyncDocumentProcessDispatcher(DocumentProcessingService documentProcessingService) {
        this.documentProcessingService = documentProcessingService;
    }

    @Override
    public String dispatch(UUID docId) {
        return documentProcessingService.processDocument(docId);
    }
}
