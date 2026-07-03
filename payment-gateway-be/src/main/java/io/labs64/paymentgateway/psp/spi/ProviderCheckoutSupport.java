package io.labs64.paymentgateway.psp.spi;

import java.util.Optional;

/**
 * Optional capability for payment providers that require a user-facing checkout session.
 * <p>
 * This capability is intended for hosted or redirect checkout flows such as
 * PayPal Checkout or Stripe Checkout. Server-to-server providers should not
 * implement it unless they need browser continuation state.
 */
public interface ProviderCheckoutSupport {

    /**
     * Gives the provider a chance to request checkout session creation before
     * payment execution starts.
     * <p>
     * The provider validates checkout request data and returns provider-owned
     * payload that the gateway persists in {@link CheckoutSession}. Returning
     * {@link Optional#empty()} means this payment attempt does not need a
     * checkout session.
     *
     * @param context immutable payment attempt context
     * @return checkout session draft when this attempt needs browser checkout state
     * @throws io.labs64.paymentgateway.exception.ValidationException when checkout request data is invalid
     */
    Optional<CheckoutSessionDraft> prepareCheckoutSession(CheckoutPreparationContext context);

    /**
     * Completes a browser checkout flow after the customer returns from the PSP.
     * <p>
     * The gateway owns lookup, persistence, and transaction updates. The provider
     * should use this method to verify PSP query parameters and produce the final
     * payment result.
     *
     * @param context checkout callback context
     * @return provider payment result to be applied by the gateway
     */
    PaymentResult completeCheckout(ProviderCheckoutContext context);

    /**
     * Handles a browser checkout cancellation after the customer returns from the PSP.
     * <p>
     * The provider should validate any PSP-specific cancellation parameters and
     * return the result that the gateway should apply to the payment transaction.
     *
     * @param context checkout callback context
     * @return provider payment result to be applied by the gateway
     */
    PaymentResult cancelCheckout(ProviderCheckoutContext context);
}
