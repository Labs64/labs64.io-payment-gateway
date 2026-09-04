package io.labs64.paymentgateway.psp.spi;

/**
 * Canonical payment transaction detail codes shared by provider adapters and
 * the payment gateway.
 *
 * <p>The top-level transaction status remains the source of terminality. These
 * codes explain the normalized provider outcome and must not be used to override
 * the gateway state-transition policy. Raw PSP statuses belong in sanitized
 * provider data.</p>
 */
public final class PaymentStatusDetailCodes {

    public static final String AWAITING_CUSTOMER = "AWAITING_CUSTOMER";
    public static final String PROCESSING = "PROCESSING";
    public static final String PROVIDER_UNAVAILABLE = "PROVIDER_UNAVAILABLE";
    public static final String PROVIDER_AUTHENTICATION_FAILED = "PROVIDER_AUTHENTICATION_FAILED";
    public static final String PROVIDER_RESPONSE_INVALID = "PROVIDER_RESPONSE_INVALID";
    public static final String COMPLETED = "COMPLETED";
    public static final String PAYMENT_FAILED = "PAYMENT_FAILED";
    public static final String DECLINED = "DECLINED";
    public static final String CANCELLED = "CANCELLED";
    public static final String EXPIRED = "EXPIRED";
    public static final String PROVIDER_REJECTED = "PROVIDER_REJECTED";

    private PaymentStatusDetailCodes() {
    }
}
