package io.labs64.moduleb.core.service;

import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

@Component
public class MessageSubscriberService {

    private static final Logger logger = LoggerFactory.getLogger(MessageSubscriberService.class);

    @Bean
    public Consumer<String> receive() {
        return message -> logger.info("Received message: {}", message);
    }

}
