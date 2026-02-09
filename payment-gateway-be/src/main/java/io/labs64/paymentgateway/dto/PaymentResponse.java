package io.labs64.paymentgateway.dto;

public record PaymentResponse(PaymentDto payment, NextActionDto nextAction) {
}
