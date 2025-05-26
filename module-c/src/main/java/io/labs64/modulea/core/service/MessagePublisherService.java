package io.labs64.modulea.core.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.web.bind.annotation.*;

@RestController
public class MessagePublisherService {

    private static final Logger logger = LoggerFactory.getLogger(MessagePublisherService.class);

    private final StreamBridge streamBridge;

    @Autowired
    public MessagePublisherService(StreamBridge streamBridge) {
        this.streamBridge = streamBridge;
    }

    @PostMapping("/publish")
    public ResponseEntity<String> publishMessage(@RequestBody String message) {
        logger.info("Publish message: {}", message);

        boolean sent = streamBridge.send("send-out-0", MessageBuilder.withPayload(message).build());

        if (sent) {
            return ResponseEntity.ok("Message sent: " + message);
        } else {
            return ResponseEntity.status(500).body("Failed to send message");
        }
    }

}
