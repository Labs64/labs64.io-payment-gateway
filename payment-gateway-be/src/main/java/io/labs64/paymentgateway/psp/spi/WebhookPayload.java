package io.labs64.paymentgateway.psp.spi;

import java.util.UUID;

public record WebhookPayload(UUID transactionId) {

}
