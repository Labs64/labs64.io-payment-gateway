package io.labs64.paymentgateway.controller;

import io.labs64.paymentgateway.dto.StripeWebhookRequest;
import io.labs64.paymentgateway.service.WebhookService;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class StripeWebhookController {
	private final WebhookService webhookService;

	@PostMapping("/webhooks/stripe")
	public ResponseEntity<Void> handleWebhook(@RequestBody StripeWebhookRequest request) {
		Map<String, Object> data = request != null ? request.data() : Map.of();
		webhookService.handleStripeWebhook(request != null ? request.type() : null, data);
		return ResponseEntity.noContent().build();
	}
}
