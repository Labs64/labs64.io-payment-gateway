package io.labs64.paymentgateway.controller;

import io.labs64.paymentgateway.dto.PaymentCreateRequest;
import io.labs64.paymentgateway.dto.PaymentResponse;
import io.labs64.paymentgateway.dto.TransactionResponse;
import io.labs64.paymentgateway.service.PaymentService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class PaymentsController {
	private final PaymentService paymentService;

	@PostMapping("/payments")
	public ResponseEntity<PaymentResponse> createPayment(@RequestBody PaymentCreateRequest request) {
		return ResponseEntity.ok(paymentService.createPayment(request));
	}

	@PostMapping("/payments/{paymentId}/pay")
	public ResponseEntity<TransactionResponse> executePayment(@PathVariable UUID paymentId,
			@RequestHeader("Idempotency-Key") String idempotencyKey) {
		return ResponseEntity.ok(paymentService.executePayment(paymentId, idempotencyKey));
	}

	@GetMapping("/payments/{paymentId}")
	public ResponseEntity<PaymentResponse> getPayment(@PathVariable UUID paymentId) {
		return ResponseEntity.ok(paymentService.getPayment(paymentId));
	}

	@PostMapping("/payments/{paymentId}/close")
	public ResponseEntity<Void> closePayment(@PathVariable UUID paymentId) {
		paymentService.closePayment(paymentId);
		return ResponseEntity.noContent().build();
	}
}
