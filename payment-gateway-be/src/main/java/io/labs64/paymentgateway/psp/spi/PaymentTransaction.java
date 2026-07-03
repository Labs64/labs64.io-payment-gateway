package io.labs64.paymentgateway.psp.spi;

import io.labs64.paymentgateway.model.PaymentTransactionStatus;

import java.util.UUID;

public record PaymentTransaction(
        UUID id,
        PaymentTransactionStatus status) {

}
