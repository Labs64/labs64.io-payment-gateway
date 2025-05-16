package com.labs64.vanguard.moduleb.core.service;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import io.dapr.Topic;
import io.dapr.client.domain.CloudEvent;

@RestController
public class MessageSubscriberService {

    @Topic(name = "sample-topic", pubsubName = "redis-pubsub")
    @PostMapping(path = "/sample-topic")
    public void handleMessage(@RequestBody(required = false) CloudEvent<String> cloudEvent) {
        String message = cloudEvent.getData();
        System.out.println("Received message: " + message);
    }

}
