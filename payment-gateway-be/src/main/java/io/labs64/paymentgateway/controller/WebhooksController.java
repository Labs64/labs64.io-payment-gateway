package io.labs64.paymentgateway.controller;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.labs64.paymentgateway.service.WebhookService;
import io.labs64.paymentgateway.v1.api.WebhooksApi;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class WebhooksController implements WebhooksApi {

    private static final Logger log = LoggerFactory.getLogger(WebhooksController.class);

    private final WebhookService webhookService;

    @Override
    public ResponseEntity<Void> handleWebhook(
            String provider,
            Map<String, Object> requestBody) {
        log.info("POST /webhooks/{} | payloadKeys={}",
                provider, requestBody != null ? requestBody.keySet() : "null");

        webhookService.processWebhook(provider, requestBody);
        return ResponseEntity.ok().build();
    }
}
