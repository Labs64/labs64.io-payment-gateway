package io.labs64.paymentgateway.service.filter;

import io.labs64.paymentgateway.model.PaymentStatus;

public record PaymentFilter(PaymentStatus status) {
}
