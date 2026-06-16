package io.labs64.paymentgateway.controller;

import java.util.Map;

import io.labs64.paymentgateway.correlation.CorrelationContextHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import io.labs64.paymentgateway.service.WebhookService;
import io.labs64.paymentgateway.api.WebhooksApi;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class WebhookController implements WebhooksApi {

    private static final Logger log = LoggerFactory.getLogger(WebhookController.class);

    private final WebhookService webhookService;

    @Override
    public ResponseEntity<Void> handleWebhook(
            String provider,
            Map<String, Object> requestBody) {
        log.info("POST /webhooks/{} | correlationId={}, payloadKeys={}",
                provider, MDC.get(CorrelationContextHolder.get().orElse("-")),
                requestBody != null ? requestBody.keySet() : "null");

        webhookService.processWebhook(provider, requestBody);
        return ResponseEntity.ok().build();
    }
}
