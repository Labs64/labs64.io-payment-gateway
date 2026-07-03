package io.labs64.paymentgateway.service.filter;

import java.util.UUID;

import io.labs64.paymentgateway.model.PaymentTransactionStatus;

public record PaymentTransactionFilter(UUID paymentId, PaymentTransactionStatus status) {
}
