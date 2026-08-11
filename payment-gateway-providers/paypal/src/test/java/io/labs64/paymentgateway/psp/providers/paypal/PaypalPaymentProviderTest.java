package io.labs64.paymentgateway.psp.providers.paypal;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import io.labs64.paymentgateway.model.OrderItem;
import io.labs64.paymentgateway.model.PurchaseOrder;
import io.labs64.paymentgateway.psp.spi.CheckoutPreparationContext;
import io.labs64.paymentgateway.psp.spi.CheckoutSession;
import io.labs64.paymentgateway.psp.spi.CheckoutSessionDraft;
import io.labs64.paymentgateway.psp.spi.Payment;
import io.labs64.paymentgateway.psp.spi.PaymentContext;
import io.labs64.paymentgateway.psp.spi.PaymentExecutionRequest;
import io.labs64.paymentgateway.psp.spi.PaymentNextActionType;
import io.labs64.paymentgateway.psp.spi.PaymentResult;
import io.labs64.paymentgateway.psp.spi.PaymentTransaction;
import io.labs64.paymentgateway.psp.spi.PaymentTransactionStatus;
import io.labs64.paymentgateway.psp.spi.PaymentWebhookContext;
import io.labs64.paymentgateway.psp.spi.PaymentWebhookResult;
import io.labs64.paymentgateway.psp.spi.ProviderCheckoutContext;
import io.labs64.paymentgateway.psp.spi.ProviderConfig;
import io.labs64.paymentgateway.psp.spi.ProviderCheckout;
import io.labs64.paymentgateway.psp.spi.ProviderCheckoutUrls;
import io.labs64.paymentgateway.psp.spi.ProviderConfigField;
import io.labs64.paymentgateway.psp.spi.ProviderValidationException;
import io.labs64.paymentgateway.psp.spi.WebhookRejectedException;
import io.labs64.paymentgateway.psp.spi.WebhookRequest;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaypalPaymentProviderTest {

    private final PaypalPaymentProvider provider = new PaypalPaymentProvider();

    @Test
    void providerReturnsPaypalIdentifier() {
        assertThat(provider.provider()).isEqualTo("paypal");
    }

    @Test
    void configFieldsDeclareRequiredPaypalCredentials() {
        final Map<String, Boolean> fields = provider.configFields().stream()
                .collect(Collectors.toMap(ProviderConfigField::name, ProviderConfigField::required));

        assertThat(fields).containsExactlyInAnyOrderEntriesOf(Map.of(
                "clientId", true,
                "clientSecret", true,
                "environment", true,
                "webhookId", true));
    }

    @Test
    void validateConfigAcceptsSandboxAndLiveEnvironments() {
        provider.validateConfig(config("sandbox"));
        provider.validateConfig(config("live"));
        provider.validateConfig(config(" SANDBOX "));
    }

    @Test
    void validateConfigRejectsUnsupportedEnvironment() {
        assertThatThrownBy(() -> provider.validateConfig(config("dev")))
                .isInstanceOf(ProviderValidationException.class)
                .hasMessage("PayPal environment must be either sandbox or live.");
    }

    @Test
    void prepareCheckoutSessionStoresReturnAndCancelUrls() {
        final CheckoutPreparationContext context = new CheckoutPreparationContext(
                null,
                null,
                new PaymentExecutionRequest(Map.of(
                        "returnUrl", "https://checkout.example.com/payment/return",
                        "cancelUrl", "https://checkout.example.com/payment/cancel")));

        final CheckoutSessionDraft draft = provider.prepareCheckoutSession(context).orElseThrow();

        assertThat(draft.payload()).containsExactlyInAnyOrderEntriesOf(Map.of(
                "returnUrl", "https://checkout.example.com/payment/return",
                "cancelUrl", "https://checkout.example.com/payment/cancel"));
        assertThat(draft.expiresAt()).isNull();
    }

    @Test
    void prepareCheckoutSessionRequiresReturnUrl() {
        final CheckoutPreparationContext context = checkoutContext(Map.of(
                "cancelUrl", "https://checkout.example.com/payment/cancel"));

        assertThatThrownBy(() -> provider.prepareCheckoutSession(context))
                .isInstanceOf(ProviderValidationException.class)
                .hasMessage("PayPal checkout requires returnUrl.");
    }

    @Test
    void prepareCheckoutSessionRequiresCancelUrl() {
        final CheckoutPreparationContext context = checkoutContext(Map.of(
                "returnUrl", "https://checkout.example.com/payment/return"));

        assertThatThrownBy(() -> provider.prepareCheckoutSession(context))
                .isInstanceOf(ProviderValidationException.class)
                .hasMessage("PayPal checkout requires cancelUrl.");
    }

    @Test
    void prepareCheckoutSessionRequiresAbsoluteUrls() {
        final CheckoutPreparationContext context = checkoutContext(Map.of(
                "returnUrl", "/payment/return",
                "cancelUrl", "https://checkout.example.com/payment/cancel"));

        assertThatThrownBy(() -> provider.prepareCheckoutSession(context))
                .isInstanceOf(ProviderValidationException.class)
                .hasMessage("PayPal checkout returnUrl must be an absolute URL.");
    }

    @Test
    void executeRequiresCheckoutSession() {
        assertThatThrownBy(() -> provider.execute(new io.labs64.paymentgateway.psp.spi.PaymentContext(null, null, null)))
                .isInstanceOf(ProviderValidationException.class)
                .hasMessage("PayPal checkout session is required.");
    }

    @Test
    void executeRejectsZeroItemQuantity() {
        assertThatThrownBy(() -> provider.execute(paymentContextWithItemQuantity(0)))
                .isInstanceOf(ProviderValidationException.class)
                .hasMessage("PayPal payment requires purchaseOrder.items[].quantity.");
    }

    @Test
    void cancelCheckoutReturnsRedirectToStoredCancelUrl() {
        final ProviderCheckoutContext context = new ProviderCheckoutContext(
                null,
                null,
                null,
                new CheckoutSession(
                        UUID.randomUUID(),
                        Map.of(
                                "returnUrl", "https://checkout.example.com/payment/return",
                                "cancelUrl", "https://checkout.example.com/payment/cancel"),
                        null,
                        null),
                Map.of("token", java.util.List.of("paypal-order")));

        final PaymentResult result = provider.cancelCheckout(context);

        assertThat(result.status()).isEqualTo(PaymentTransactionStatus.FAILED);
        assertThat(result.nextAction().type()).isEqualTo(PaymentNextActionType.REDIRECT);
        assertThat(result.nextAction().details()).containsEntry("url", "https://checkout.example.com/payment/cancel");
    }

    @Test
    void extractPaymentTransactionIdReadsCaptureInvoiceId() {
        final UUID transactionId = UUID.randomUUID();

        assertThat(provider.extractPaymentTransactionId(webhookRequest(capturePayload(
                transactionId, "PAYMENT.CAPTURE.COMPLETED", "COMPLETED"))))
                .isEqualTo(transactionId);
    }

    @Test
    void extractPaymentTransactionIdReadsOrderPurchaseUnitInvoiceId() {
        final UUID transactionId = UUID.randomUUID();
        final String payload = """
                {
                  "id": "WH-ORDER",
                  "event_type": "CHECKOUT.ORDER.APPROVED",
                  "resource": {
                    "id": "ORDER-1",
                    "status": "APPROVED",
                    "purchase_units": [{"invoice_id": "%s"}]
                  }
                }
                """.formatted(transactionId);

        assertThat(provider.extractPaymentTransactionId(webhookRequest(payload))).isEqualTo(transactionId);
    }

    @Test
    void extractPaymentTransactionIdRejectsMissingInvoiceId() {
        final WebhookRequest request = webhookRequest("""
                {"id":"WH-1","event_type":"PAYMENT.CAPTURE.COMPLETED","resource":{"id":"CAPTURE-1"}}
                """);

        assertThatThrownBy(() -> provider.extractPaymentTransactionId(request))
                .isInstanceOf(WebhookRejectedException.class)
                .hasMessage("PayPal webhook does not contain paymentTransactionId.");
    }

    @Test
    void handleWebhookVerifiesAndMapsCompletedCapture() {
        final UUID transactionId = UUID.randomUUID();
        final AtomicReference<String> verifiedWebhookId = new AtomicReference<>();
        final PaypalPaymentProvider webhookProvider = new PaypalPaymentProvider(
                (client, webhookId, request) -> verifiedWebhookId.set(webhookId));
        final WebhookRequest request = webhookRequest(capturePayload(
                transactionId, "PAYMENT.CAPTURE.COMPLETED", "COMPLETED"));

        final PaymentWebhookResult result = webhookProvider.handleWebhook(webhookContext(transactionId, request));

        assertThat(verifiedWebhookId).hasValue("paypal-webhook-id");
        assertThat(result.status()).isEqualTo(PaymentTransactionStatus.SUCCESS);
        assertThat(result.statusDetails()).isEqualTo(
                new io.labs64.paymentgateway.psp.spi.StatusDetails(
                        "SUCCESS", "PayPal webhook mapped to payment status SUCCESS."));
        assertThat(result.pspData())
                .containsEntry("eventId", "WH-1")
                .containsEntry("eventType", "PAYMENT.CAPTURE.COMPLETED")
                .containsEntry("resourceId", "CAPTURE-1")
                .containsEntry("paypalStatus", "COMPLETED")
                .containsEntry("orderId", "ORDER-1");
    }

    @Test
    void handleWebhookRejectsWhenPaypalVerificationFails() {
        final UUID transactionId = UUID.randomUUID();
        final PaypalPaymentProvider webhookProvider = new PaypalPaymentProvider((client, webhookId, request) -> {
            throw new WebhookRejectedException("PayPal webhook verification failed.");
        });
        final PaymentWebhookContext context = webhookContext(
                transactionId,
                webhookRequest(capturePayload(transactionId, "PAYMENT.CAPTURE.COMPLETED", "COMPLETED")));

        assertThatThrownBy(() -> webhookProvider.handleWebhook(context))
                .isInstanceOf(WebhookRejectedException.class)
                .hasMessage("PayPal webhook verification failed.");
    }

    @Test
    void handleWebhookMapsPendingCapture() {
        final UUID transactionId = UUID.randomUUID();
        final PaypalPaymentProvider webhookProvider = new PaypalPaymentProvider((client, webhookId, request) -> { });

        final PaymentWebhookResult result = webhookProvider.handleWebhook(webhookContext(
                transactionId,
                webhookRequest(capturePayload(transactionId, "PAYMENT.CAPTURE.PENDING", "PENDING"))));

        assertThat(result.status()).isEqualTo(PaymentTransactionStatus.PENDING);
        assertThat(result.statusDetails().code()).isEqualTo("PENDING");
    }

    @Test
    void handleWebhookMapsDeniedCaptureToFailed() {
        final UUID transactionId = UUID.randomUUID();
        final PaypalPaymentProvider webhookProvider = new PaypalPaymentProvider((client, webhookId, request) -> { });

        final PaymentWebhookResult result = webhookProvider.handleWebhook(webhookContext(
                transactionId,
                webhookRequest(capturePayload(transactionId, "PAYMENT.CAPTURE.DENIED", "DENIED"))));

        assertThat(result.status()).isEqualTo(PaymentTransactionStatus.FAILED);
        assertThat(result.statusDetails().code()).isEqualTo("FAILED");
    }

    @Test
    void handleWebhookRejectsVerifiedTransactionMismatch() {
        final UUID restoredTransactionId = UUID.randomUUID();
        final UUID payloadTransactionId = UUID.randomUUID();
        final PaypalPaymentProvider webhookProvider = new PaypalPaymentProvider((client, webhookId, request) -> { });

        assertThatThrownBy(() -> webhookProvider.handleWebhook(webhookContext(
                restoredTransactionId,
                webhookRequest(capturePayload(
                        payloadTransactionId, "PAYMENT.CAPTURE.COMPLETED", "COMPLETED")))))
                .isInstanceOf(WebhookRejectedException.class)
                .hasMessage("PayPal webhook transaction does not match restored transaction.");
    }

    private static Map<String, String> config(final String environment) {
        return Map.of(
                "clientId", "client-id",
                "clientSecret", "client-secret",
                "environment", environment,
                "webhookId", "paypal-webhook-id");
    }

    private static PaymentWebhookContext webhookContext(
            final UUID transactionId,
            final WebhookRequest request) {
        return new PaymentWebhookContext(
                new PaymentTransaction(transactionId, PaymentTransactionStatus.PENDING),
                new ProviderConfig("paypal", config("sandbox"), "PayPal", null),
                request);
    }

    private static WebhookRequest webhookRequest(final String payload) {
        return new WebhookRequest("paypal", payload, Map.of(), Map.of());
    }

    private static String capturePayload(
            final UUID transactionId,
            final String eventType,
            final String status) {
        return """
                {
                  "id": "WH-1",
                  "event_type": "%s",
                  "resource": {
                    "id": "CAPTURE-1",
                    "status": "%s",
                    "invoice_id": "%s",
                    "supplementary_data": {
                      "related_ids": {"order_id": "ORDER-1"}
                    }
                  }
                }
                """.formatted(eventType, status, transactionId);
    }

    private static CheckoutPreparationContext checkoutContext(final Map<String, Object> checkout) {
        return new CheckoutPreparationContext(null, null, new PaymentExecutionRequest(checkout));
    }

    private static PaymentContext paymentContextWithItemQuantity(final Integer quantity) {
        return new PaymentContext(
                new Payment(
                        UUID.randomUUID(),
                        null,
                        "Test payment",
                        null,
                        new PurchaseOrder()
                                .currency("USD")
                                .grossAmount(3000L)
                                .items(List.of(new OrderItem()
                                        .name("Widget")
                                        .price(3000L)
                                        .quantity(quantity))),
                        null,
                        null,
                        null),
                new PaymentTransaction(UUID.randomUUID(), PaymentTransactionStatus.PENDING),
                new ProviderConfig("paypal", config("sandbox"), "PayPal", null),
                PaymentExecutionRequest.empty(),
                new ProviderCheckout(
                        new CheckoutSession(UUID.randomUUID(), Map.of(), null, null),
                        new ProviderCheckoutUrls(
                                "https://gateway.example/paypal/return",
                                "https://gateway.example/paypal/cancel")));
    }
}
