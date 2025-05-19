package io.labs64.moduleb.core.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import io.dapr.Topic;
import io.dapr.client.domain.CloudEvent;

@RestController
public class MessageSubscriberService {

    private static final Logger logger = LoggerFactory.getLogger(MessageSubscriberService.class);

    @Topic(name = "sample-topic", pubsubName = "redis-pubsub")
    @PostMapping(path = "/sample-topic")
    public void handleMessage(@RequestBody(required = false) CloudEvent<String> cloudEvent) {
        String message = cloudEvent.getData();
        logger.info("Received message: {}", message);
    }

}
