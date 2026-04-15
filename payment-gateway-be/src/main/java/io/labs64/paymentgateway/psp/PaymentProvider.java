package io.labs64.paymentgateway.psp;

import java.util.Map;

import io.labs64.paymentgateway.entity.PaymentEntity;
import io.labs64.paymentgateway.entity.TransactionEntity;

/**
 * Core PSP abstraction interface using the Strategy Pattern.
 * Each payment provider (Stripe, PayPal, NoOp) implements this interface.
 * <p>
 * Adding a new PSP requires:
 * <ol>
 *   <li>Implement this interface</li>
 *   <li>Annotate with {@code @Component}</li>
 *   <li>Add YAML configuration entry</li>
 * </ol>
 * No changes to existing code should be required.
 */
public interface PaymentProvider {

    /**
     * Returns the unique provider ID matching the YAML configuration {@code id} field.
     *
     * @return provider identifier (e.g., "stripe", "paypal", "noop")
     */
    String getProviderId();

    /**
     * Execute a payment through this PSP.
     *
     * @param payment   the payment entity with details
     * @param pspConfig tenant-specific PSP configuration
     * @return PSP response with status, references, and optional next action
     */
    PspPaymentResponse executePayment(PaymentEntity payment, Map<String, String> pspConfig);

    /**
     * Verify a webhook notification from this PSP.
     *
     * @param payload   the raw webhook payload
     * @param pspConfig tenant-specific PSP configuration
     * @return parsed webhook result with transaction status update
     */
    PspWebhookResult verifyWebhook(Map<String, Object> payload, Map<String, String> pspConfig);

    /**
     * Check if this provider supports the given currency.
     *
     * @param currency ISO-4217 currency code
     * @return true if supported
     */
    default boolean supportsCurrency(final String currency) {
        return true;
    }

    /**
     * Check if this provider supports recurring payments.
     *
     * @return true if recurring payments are supported
     */
    default boolean supportsRecurring() {
        return false;
    }
}
