package io.labs64.paymentgateway.psp.spi;

import java.util.UUID;

public record PaymentTransaction(
        UUID id,
        PaymentTransactionStatus status) {

}
