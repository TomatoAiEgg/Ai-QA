package cn.net.tomatoegg.ai.mq.rocketmq.document.consumer;

import cn.net.tomatoegg.ai.mq.rocketmq.document.message.DocumentProcessMessage;
import cn.net.tomatoegg.ai.service.document.DocumentProcessingService;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.common.consumer.ConsumeFromWhere;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.apache.rocketmq.spring.core.RocketMQPushConsumerLifecycleListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@ConditionalOnProperty(prefix = "ai.service.rocketmq.document-process", name = "enabled", havingValue = "true")
@RocketMQMessageListener(
        nameServer = "${ai.service.rocketmq.document-process.name-server}",
        topic = "${ai.service.rocketmq.document-process.topic}",
        selectorExpression = "${ai.service.rocketmq.document-process.tag}",
        consumerGroup = "${ai.service.rocketmq.document-process.consumer-group}"
)
public class DocumentProcessConsumer implements RocketMQListener<DocumentProcessMessage>, RocketMQPushConsumerLifecycleListener {

    private final DocumentProcessingService documentProcessingService;

    public DocumentProcessConsumer(DocumentProcessingService documentProcessingService) {
        this.documentProcessingService = documentProcessingService;
    }

    @Override
    public void onMessage(DocumentProcessMessage message) {
        try {
            documentProcessingService.processDocument(message.getDocId());
        } catch (Exception ex) {
            log.error("消费文档处理消息失败, docId={}", message == null ? null : message.getDocId(), ex);
            throw ex;
        }
    }

    @Override
    public void prepareStart(DefaultMQPushConsumer consumer) {
        consumer.setConsumeFromWhere(ConsumeFromWhere.CONSUME_FROM_FIRST_OFFSET);
        log.info("Initialized document process consumer, group={}, topics={}, consumeFromWhere={}",
                consumer.getConsumerGroup(), consumer.getSubscription().keySet(), consumer.getConsumeFromWhere());
    }
}
