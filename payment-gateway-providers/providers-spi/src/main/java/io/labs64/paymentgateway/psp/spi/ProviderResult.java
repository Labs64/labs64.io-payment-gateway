package io.labs64.paymentgateway.psp.spi;

import java.util.Map;

/**
 * Common provider result data that the gateway applies to a payment transaction.
 */
public interface ProviderResult {

    String provider();

    PaymentTransactionStatus status();

    Map<String, Object> pspData();

    StatusDetails statusDetails();
}
