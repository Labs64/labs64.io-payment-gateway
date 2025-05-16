package com.labs64.vanguard.modulea.core.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.dapr.client.DaprClient;

@RestController
public class MessagePublisherService {

    @Autowired
    private DaprClient daprClient;

    @PostMapping("/publish")
    public void publishMessage(@RequestParam String message) {
        System.out.println("Publish message: " + message);
        daprClient.publishEvent("redis-pubsub", "sample-topic", message).block();
    }

}
