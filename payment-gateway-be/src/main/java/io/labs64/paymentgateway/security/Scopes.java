package io.labs64.paymentgateway.security;

/**
 * Payment Gateway OAuth2 scope names.
 */
public final class Scopes {

    public static final String PAYMENT_PROVIDER_READ = "payment-provider:read";
    public static final String PAYMENT_PROVIDER_WRITE = "payment-provider:write";
    public static final String PAYMENT_READ = "payment:read";
    public static final String PAYMENT_WRITE = "payment:write";
    public static final String PAYMENT_PAY = "payment:pay";
    public static final String PAYMENT_TRANSACTION_READ = "payment-transaction:read";

    private Scopes() {
    }
}
