package io.labs64.paymentgateway.mapper;

import io.labs64.paymentgateway.entity.Payment;
import io.labs64.paymentgateway.entity.PaymentTransaction;
import io.labs64.paymentgateway.dto.NextActionDto;
import io.labs64.paymentgateway.dto.PaymentDto;
import io.labs64.paymentgateway.dto.TransactionDto;
import io.labs64.paymentgateway.service.JsonService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PaymentMapper {
	private final JsonService jsonService;

	public PaymentDto toDto(Payment payment) {
		return new PaymentDto(
				payment.getId().toString(),
				payment.getStatus().name(),
				payment.getPaymentMethodId(),
				payment.getTenantId(),
				payment.isRecurring(),
				jsonService.toMap(payment.getPurchaseOrder()),
				jsonService.toMap(payment.getBillingInfo()),
				jsonService.toMap(payment.getShippingInfo()),
				jsonService.toMap(payment.getExtra()),
				payment.getCreatedAt());
	}

	public NextActionDto toNextAction(Payment payment) {
		if (payment.getNextActionType() == null) {
			return null;
		}
		return new NextActionDto(
				payment.getNextActionType(),
				jsonService.toMap(payment.getNextActionDetails()));
	}

	public TransactionDto toDto(PaymentTransaction transaction) {
		return new TransactionDto(
				transaction.getId().toString(),
				transaction.getPaymentId().toString(),
				transaction.getStatus().name(),
				jsonService.toMap(transaction.getPspData()),
				transaction.getCreatedAt());
	}
}
