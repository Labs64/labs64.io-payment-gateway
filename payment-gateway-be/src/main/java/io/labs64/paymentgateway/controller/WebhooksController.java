package io.labs64.paymentgateway.controller;

import io.labs64.paymentgateway.v1.api.WebhooksApi;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1")
public class WebhooksController implements WebhooksApi {

    private static final Logger log = LoggerFactory.getLogger(WebhooksController.class);

    @Override
    public ResponseEntity<Void> handleWebhook(
            String provider,
            Map<String, Object> requestBody) {
        log.debug("POST /webhooks/{} - Received webhook notification | payloadKeys={}",
                provider, requestBody != null ? requestBody.keySet() : "null");

        // TODO: Implement - route to appropriate PSP adapter, verify webhook signature,
        //       restore correlationId from original payment record, update transaction status,
        //       publish payment.finalized event
        log.debug("POST /webhooks/{} - Stub: webhook acknowledged", provider);

        return ResponseEntity.ok().build();
    }
}
