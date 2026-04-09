package cn.net.tomatoegg.ai.mq.rocketmq.document.config;

import cn.net.tomatoegg.ai.mq.rocketmq.document.consumer.DocumentProcessConsumer;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.apache.rocketmq.spring.support.DefaultRocketMQListenerContainer;
import org.apache.rocketmq.spring.support.RocketMQMessageConverter;
import org.apache.rocketmq.spring.support.RocketMQUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.env.Environment;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.springframework.util.StringUtils;

@Configuration
@ConditionalOnProperty(prefix = "ai.service.rocketmq.document-process", name = "enabled", havingValue = "true")
public class DocumentRocketMqConfiguration {

    @Bean
    @ConditionalOnMissingBean(RocketMQMessageConverter.class)
    public RocketMQMessageConverter documentProcessRocketMQMessageConverter() {
        return new RocketMQMessageConverter();
    }

    @Bean
    @ConditionalOnMissingBean(name = "documentProcessRocketMQTemplate")
    public RocketMQTemplate documentProcessRocketMQTemplate(
            RocketMQMessageConverter rocketMQMessageConverter,
            @Value("${ai.service.rocketmq.document-process.name-server:}") String nameServer,
            @Value("${ai.service.rocketmq.document-process.producer-group:aiqa-document-process-producer-group}") String producerGroup,
            @Value("${ai.service.rocketmq.document-process.namespace:}") String namespace,
            @Value("${ai.service.rocketmq.document-process.access-key:}") String accessKey,
            @Value("${ai.service.rocketmq.document-process.secret-key:}") String secretKey,
            @Value("${ai.service.rocketmq.document-process.enable-msg-trace:false}") boolean enableMsgTrace,
            @Value("${ai.service.rocketmq.document-process.customized-trace-topic:}") String customizedTraceTopic,
            @Value("${ai.service.rocketmq.document-process.send-timeout:3000}") int sendTimeout,
            @Value("${ai.service.rocketmq.document-process.retry-times-when-send-failed:2}") int retryTimesWhenSendFailed,
            @Value("${ai.service.rocketmq.document-process.retry-times-when-send-async-failed:2}") int retryTimesWhenSendAsyncFailed,
            @Value("${ai.service.rocketmq.document-process.max-message-size:4194304}") int maxMessageSize,
            @Value("${ai.service.rocketmq.document-process.compress-message-body-threshold:4096}") int compressMessageBodyThreshold,
            @Value("${ai.service.rocketmq.document-process.retry-another-broker-when-not-store-ok:false}") boolean retryAnotherBrokerWhenNotStoreOk,
            @Value("${ai.service.rocketmq.document-process.tls-enable:false}") boolean tlsEnable,
            @Value("${ai.service.rocketmq.document-process.instance-name:}") String instanceName) {
        if (!StringUtils.hasText(nameServer)) {
            throw new IllegalStateException("[ai.service.rocketmq.document-process.name-server] must not be blank when RocketMQ document processing is enabled");
        }

        DefaultMQProducer producer = RocketMQUtil.createDefaultMQProducer(
                producerGroup,
                accessKey,
                secretKey,
                enableMsgTrace,
                customizedTraceTopic
        );
        producer.setNamesrvAddr(nameServer);
        producer.setSendMsgTimeout(sendTimeout);
        producer.setRetryTimesWhenSendFailed(retryTimesWhenSendFailed);
        producer.setRetryTimesWhenSendAsyncFailed(retryTimesWhenSendAsyncFailed);
        producer.setMaxMessageSize(maxMessageSize);
        producer.setCompressMsgBodyOverHowmuch(compressMessageBodyThreshold);
        producer.setRetryAnotherBrokerWhenNotStoreOK(retryAnotherBrokerWhenNotStoreOk);
        producer.setUseTLS(tlsEnable);

        if (StringUtils.hasText(namespace)) {
            producer.setNamespace(namespace);
        }
        if (StringUtils.hasText(instanceName)) {
            producer.setInstanceName(instanceName);
        }

        RocketMQTemplate template = new RocketMQTemplate();
        template.setProducer(producer);
        template.setMessageConverter(rocketMQMessageConverter.getMessageConverter());
        return template;
    }

    @Bean(name = "documentProcessListenerContainer", destroyMethod = "destroy")
    @ConditionalOnMissingBean(name = "documentProcessListenerContainer")
    public DefaultRocketMQListenerContainer documentProcessListenerContainer(
            DocumentProcessConsumer documentProcessConsumer,
            RocketMQMessageConverter rocketMQMessageConverter,
            Environment environment) {
        RocketMQMessageListener annotation = AnnotatedElementUtils.findMergedAnnotation(
                DocumentProcessConsumer.class,
                RocketMQMessageListener.class
        );
        if (annotation == null) {
            throw new IllegalStateException("@RocketMQMessageListener is required on DocumentProcessConsumer");
        }

        DefaultRocketMQListenerContainer container = new DefaultRocketMQListenerContainer();
        container.setName("documentProcessListenerContainer");
        container.setRocketMQMessageListener(annotation);
        container.setNameServer(environment.resolvePlaceholders(annotation.nameServer()));
        container.setTopic(environment.resolvePlaceholders(annotation.topic()));
        container.setConsumerGroup(environment.resolvePlaceholders(annotation.consumerGroup()));
        container.setTlsEnable(environment.resolvePlaceholders(annotation.tlsEnable()));
        String selectorExpression = environment.resolvePlaceholders(annotation.selectorExpression());
        if (StringUtils.hasText(selectorExpression)) {
            container.setSelectorExpression(selectorExpression);
        }
        container.setRocketMQListener(documentProcessConsumer);
        container.setMessageConverter(rocketMQMessageConverter.getMessageConverter());
        return container;
    }
}
