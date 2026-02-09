package io.labs64.paymentgateway.controller;

import io.labs64.paymentgateway.dto.TransactionResponse;
import io.labs64.paymentgateway.service.PaymentService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class TransactionsController {
	private final PaymentService paymentService;

	@GetMapping("/transactions/{transactionId}")
	public ResponseEntity<TransactionResponse> getTransaction(@PathVariable UUID transactionId) {
		return ResponseEntity.ok(paymentService.getTransaction(transactionId));
	}
}
