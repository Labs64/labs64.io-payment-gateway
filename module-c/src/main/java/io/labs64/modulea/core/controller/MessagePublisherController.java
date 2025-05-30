package io.labs64.modulea.core.controller;

import io.labs64.api.MessagePublisherApi;
import io.labs64.modulea.core.service.MessagePublisherService;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MessagePublisherController implements MessagePublisherApi {

    private final MessagePublisherService messagePublisherService;

    public MessagePublisherController(MessagePublisherService messagePublisherService) {
        this.messagePublisherService = messagePublisherService;
    }

    @Override
    public ResponseEntity<String> publishPost(String body) {
        boolean res = messagePublisherService.publishMessage(body);
        if (res) {
            return ResponseEntity.ok("Message sent successfully");
        } else {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Failed to send message");
        }
    }

}
