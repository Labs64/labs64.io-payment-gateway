package io.labs64.modulea.core.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Service;

@Service
public class MessagePublisherService {

    private static final Logger logger = LoggerFactory.getLogger(MessagePublisherService.class);

    private final StreamBridge streamBridge;

    @Value("${app.default-broker}")
    private String defaultBroker;

    @Autowired
    public MessagePublisherService(StreamBridge streamBridge) {
        this.streamBridge = streamBridge;
    }

    public boolean publishMessage(String message) {
        logger.info("Publish message: '{}' to '{}'", message, defaultBroker + "-out-0");
        return streamBridge.send(defaultBroker + "-out-0", MessageBuilder.withPayload(message).build());
    }

}
