package io.labs64.paymentgateway.psp.spi;

/**
 * Core payment provider contract.
 *
 * <p>An adapter owns PSP communication and translates a definitive PSP business
 * outcome into a normal {@link PaymentResult}. A returned result is the only
 * provider signal that permits the gateway to transition a payment transaction.</p>
 *
 * <p>When the adapter cannot determine the PSP outcome after execution starts,
 * it must throw {@link ProviderExecutionException}. Input validation that is
 * guaranteed to have no PSP payment side effect uses
 * {@link ProviderValidationException}. Providers must not persist gateway state
 * or publish gateway events.</p>
 */
public interface PaymentProvider {

    String provider();

    /**
     * Executes a prepared payment attempt.
     *
     * @param context immutable gateway and provider execution context
     * @return definitive or non-terminal normalized provider result
     * @throws ProviderExecutionException when no definitive PSP outcome is available
     * @throws ProviderValidationException only when validation is side-effect-free
     */
    PaymentResult execute(PaymentContext context);
}
