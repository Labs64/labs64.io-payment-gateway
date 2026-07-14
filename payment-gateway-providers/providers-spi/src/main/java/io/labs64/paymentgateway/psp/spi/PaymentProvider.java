package io.labs64.paymentgateway.psp.spi;

public interface PaymentProvider {

    String provider();

    PaymentResult execute(PaymentContext context);
}
