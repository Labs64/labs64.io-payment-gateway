package io.labs64.modulea.core.controller;

import io.labs64.api.LogPublisherApi;
import io.labs64.model.LogMessage;
import io.labs64.modulea.core.service.MessagePublisherService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class LogPublisherController implements LogPublisherApi {

    private final MessagePublisherService messagePublisherService;

    public LogPublisherController(MessagePublisherService messagePublisherService) {
        this.messagePublisherService = messagePublisherService;
    }

    @Override
    public ResponseEntity<String> publishLogsPost(LogMessage logMessage) {
        boolean res = messagePublisherService.publishMessage(logMessage.toString());
        if (res) {
            return ResponseEntity.ok("Message sent successfully");
        } else {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Failed to send message");
        }
    }
}
