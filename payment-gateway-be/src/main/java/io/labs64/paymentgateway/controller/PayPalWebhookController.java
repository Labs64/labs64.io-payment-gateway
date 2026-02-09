package io.labs64.paymentgateway.controller;

import io.labs64.paymentgateway.dto.PayPalWebhookRequest;
import io.labs64.paymentgateway.service.WebhookService;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class PayPalWebhookController {
	private final WebhookService webhookService;

	@PostMapping("/webhooks/paypal")
	public ResponseEntity<Void> handleWebhook(@RequestBody PayPalWebhookRequest request) {
		Map<String, Object> resource = request != null ? request.resource() : Map.of();
		webhookService.handlePayPalWebhook(request != null ? request.eventType() : null, resource);
		return ResponseEntity.noContent().build();
	}
}
