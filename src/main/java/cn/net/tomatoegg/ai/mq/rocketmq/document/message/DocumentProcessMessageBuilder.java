package cn.net.tomatoegg.ai.mq.rocketmq.document.message;

import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class DocumentProcessMessageBuilder {

    public DocumentProcessMessage build(UUID docId) {
        return DocumentProcessMessage.builder()
                .docId(docId)
                .build();
    }
}
