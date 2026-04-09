package cn.net.tomatoegg.ai.mq.rocketmq.document.producer;

import cn.net.tomatoegg.ai.mq.rocketmq.document.message.DocumentProcessMessageBuilder;
import cn.net.tomatoegg.ai.service.document.DocumentProcessDispatcher;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Slf4j
@Service
@ConditionalOnProperty(prefix = "ai.service.rocketmq.document-process", name = "enabled", havingValue = "true")
public class DocumentProcessProducer implements DocumentProcessDispatcher {

    private final RocketMQTemplate rocketMQTemplate;
    private final DocumentProcessMessageBuilder messageBuilder;

    @Value("${ai.service.rocketmq.document-process.topic}")
    private String topic;

    @Value("${ai.service.rocketmq.document-process.tag:}")
    private String tag;

    public DocumentProcessProducer(@Qualifier("documentProcessRocketMQTemplate") RocketMQTemplate documentProcessRocketMQTemplate,
                                   DocumentProcessMessageBuilder messageBuilder) {
        this.rocketMQTemplate = documentProcessRocketMQTemplate;
        this.messageBuilder = messageBuilder;
    }

    @Override
    public String dispatch(UUID docId) {
        String destination = tag == null || tag.isBlank() ? topic : topic + ":" + tag;
        rocketMQTemplate.syncSend(destination, messageBuilder.build(docId));
        log.info("文档处理消息已发送, docId={}", docId);
        return "文档上传成功，已进入后台处理队列";
    }
}
