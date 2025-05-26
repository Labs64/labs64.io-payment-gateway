package io.labs64.modulea.core.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.dapr.client.DaprClient;

@RestController
public class MessagePublisherService {

    private static final Logger logger = LoggerFactory.getLogger(MessagePublisherService.class);

    @Autowired
    private DaprClient daprClient;

    @PostMapping("/publish")
    public void publishMessage(@RequestParam String message) {
        logger.info("Publish message: {}", message);
        daprClient.publishEvent("redis-pubsub", "sample-topic", message).block();
    }

}