package io.labs64.paymentgateway.psp.providers.stripe;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import com.stripe.exception.ApiConnectionException;
import com.stripe.exception.ApiException;
import com.stripe.exception.AuthenticationException;
import com.stripe.exception.PermissionException;
import io.labs64.paymentgateway.psp.spi.CheckoutPreparationContext;
import io.labs64.paymentgateway.psp.spi.CheckoutSessionDraft;
import io.labs64.paymentgateway.model.PurchaseOrder;
import io.labs64.paymentgateway.psp.spi.Payment;
import io.labs64.paymentgateway.psp.spi.PaymentExecutionRequest;
import io.labs64.paymentgateway.psp.spi.PaymentTransaction;
import io.labs64.paymentgateway.psp.spi.PaymentTransactionStatus;
import io.labs64.paymentgateway.psp.spi.PaymentWebhookContext;
import io.labs64.paymentgateway.psp.spi.PaymentWebhookResult;
import io.labs64.paymentgateway.psp.spi.ProviderConfig;
import io.labs64.paymentgateway.psp.spi.ProviderConfigField;
import io.labs64.paymentgateway.psp.spi.ProviderValidationException;
import io.labs64.paymentgateway.psp.spi.StatusDetails;
import io.labs64.paymentgateway.psp.spi.WebhookRejectedException;
import io.labs64.paymentgateway.psp.spi.WebhookRequest;
import org.junit.jupiter.api.Test;

import static io.labs64.paymentgateway.psp.spi.ProviderExecutionFailure.AUTHENTICATION_FAILED;
import static io.labs64.paymentgateway.psp.spi.ProviderExecutionFailure.UNAVAILABLE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StripePaymentProviderTest {

    private static final String WEBHOOK_SECRET = "whsec_test_secret";

    private final StripePaymentProvider provider = new StripePaymentProvider(
            new StripeClientFactory(new StripeClientProperties(null)));

    @Test
    void providerReturnsStripeIdentifier() {
        assertThat(provider.provider()).isEqualTo("stripe");
    }

    @Test
    void configFieldsRequireApiAndWebhookSecrets() {
        final Map<String, Boolean> fields = provider.configFields().stream()
                .collect(Collectors.toMap(ProviderConfigField::name, ProviderConfigField::required));

        assertThat(fields).containsExactlyInAnyOrderEntriesOf(Map.of(
                "secretKey", true,
                "webhookSecret", true));
    }

    @Test
    void validateConfigAcceptsStripeSecrets() {
        provider.validateConfig(config());
        provider.validateConfig(Map.of(
                "secretKey", "rk_live_restricted",
                "webhookSecret", "whsec_live_secret"));
    }

    @Test
    void validateConfigRejectsInvalidApiKey() {
        assertThatThrownBy(() -> provider.validateConfig(Map.of(
                "secretKey", "invalid",
                "webhookSecret", WEBHOOK_SECRET)))
                .isInstanceOf(ProviderValidationException.class)
                .hasMessageContaining("secretKey");
    }

    @Test
    void validateConfigRejectsInvalidWebhookSecret() {
        assertThatThrownBy(() -> provider.validateConfig(Map.of(
                "secretKey", "sk_test_secret",
                "webhookSecret", "invalid")))
                .isInstanceOf(ProviderValidationException.class)
                .hasMessageContaining("webhookSecret");
    }

    @Test
    void classifiesAuthenticationAndPermissionFailuresAsAuthenticationFailures() {
        assertThat(StripePaymentProvider.classifyExecutionFailure(
                new AuthenticationException("Invalid API key.", null, null, 401)))
                .isEqualTo(AUTHENTICATION_FAILED);
        assertThat(StripePaymentProvider.classifyExecutionFailure(
                new PermissionException("Insufficient permissions.", null, null, 403)))
                .isEqualTo(AUTHENTICATION_FAILED);
    }

    @Test
    void classifiesOtherApiAndConnectionFailuresAsUnavailable() {
        assertThat(StripePaymentProvider.classifyExecutionFailure(
                new ApiException("Stripe server error.", null, null, 500, null)))
                .isEqualTo(UNAVAILABLE);
        assertThat(StripePaymentProvider.classifyExecutionFailure(
                new ApiConnectionException("Connection failed.")))
                .isEqualTo(UNAVAILABLE);
    }

    @Test
    void prepareCheckoutSessionStoresTenantReturnAndCancelUrls() {
        final CheckoutPreparationContext context = checkoutContext(Map.of(
                "returnUrl", "https://checkout.example.com/return",
                "cancelUrl", "https://checkout.example.com/cancel"));

        final CheckoutSessionDraft draft = provider.prepareCheckoutSession(context).orElseThrow();

        assertThat(draft.payload()).containsExactlyInAnyOrderEntriesOf(Map.of(
                "returnUrl", "https://checkout.example.com/return",
                "cancelUrl", "https://checkout.example.com/cancel"));
    }

    @Test
    void extractPaymentTransactionIdReadsStripeObjectMetadata() {
        final UUID transactionId = UUID.randomUUID();

        assertThat(provider.extractPaymentTransactionId(request(payload(transactionId, "paid"), null)))
                .isEqualTo(transactionId);
    }

    @Test
    void extractPaymentTransactionIdRejectsMissingMetadata() {
        final String payload = """
                {"id":"evt_test","object":"event","type":"checkout.session.completed",
                 "data":{"object":{"id":"cs_test","object":"checkout.session","metadata":{}}}}
                """;

        assertThatThrownBy(() -> provider.extractPaymentTransactionId(request(payload, null)))
                .isInstanceOf(WebhookRejectedException.class)
                .hasMessageContaining("paymentTransactionId");
    }

    @Test
    void handleWebhookMapsSignedPaidCheckoutToSuccess() throws Exception {
        final UUID transactionId = UUID.randomUUID();
        final String payload = payload(transactionId, "paid");
        final WebhookRequest request = request(payload, signature(payload, WEBHOOK_SECRET));

        final PaymentWebhookResult result = provider.handleWebhook(context(transactionId, request));

        assertThat(result.provider()).isEqualTo("stripe");
        assertThat(result.status()).isEqualTo(PaymentTransactionStatus.SUCCESS);
        assertThat(result.statusDetails()).isEqualTo(new StatusDetails(
                "COMPLETED", "Stripe webhook mapped to payment status SUCCESS."));
        assertThat(result.pspData())
                .containsEntry("eventId", "evt_test")
                .containsEntry("eventType", "checkout.session.completed")
                .containsEntry("stripeObjectId", "cs_test");
    }

    @Test
    void handleWebhookMapsSignedUnpaidCheckoutToPending() throws Exception {
        final UUID transactionId = UUID.randomUUID();
        final String payload = payload(transactionId, "unpaid");
        final WebhookRequest request = request(payload, signature(payload, WEBHOOK_SECRET));

        final PaymentWebhookResult result = provider.handleWebhook(context(transactionId, request));

        assertThat(result.status()).isEqualTo(PaymentTransactionStatus.PENDING);
        assertThat(result.statusDetails().code()).isEqualTo("PROCESSING");
    }

    @Test
    void handleWebhookRejectsInvalidSignature() {
        final UUID transactionId = UUID.randomUUID();
        final String payload = payload(transactionId, "paid");

        assertThatThrownBy(() -> provider.handleWebhook(
                context(transactionId, request(payload, "t=1,v1=invalid"))))
                .isInstanceOf(WebhookRejectedException.class)
                .hasMessage("Stripe webhook verification failed.");
    }

    @Test
    void handleWebhookRejectsVerifiedTransactionMismatch() throws Exception {
        final UUID payloadTransactionId = UUID.randomUUID();
        final String payload = payload(payloadTransactionId, "paid");

        assertThatThrownBy(() -> provider.handleWebhook(context(
                UUID.randomUUID(),
                request(payload, signature(payload, WEBHOOK_SECRET)))))
                .isInstanceOf(WebhookRejectedException.class)
                .hasMessageContaining("does not match");
    }

    private static CheckoutPreparationContext checkoutContext(final Map<String, Object> checkout) {
        return new CheckoutPreparationContext(
                new Payment(
                        UUID.randomUUID(),
                        null,
                        "Test payment",
                        null,
                        new PurchaseOrder().currency("USD").grossAmount(3000L),
                        null,
                        null,
                        null),
                new ProviderConfig("stripe", config(), "Stripe", null),
                new PaymentExecutionRequest(checkout));
    }

    private static PaymentWebhookContext context(
            final UUID transactionId,
            final WebhookRequest request) {
        return new PaymentWebhookContext(
                new PaymentTransaction(transactionId, PaymentTransactionStatus.PENDING),
                new ProviderConfig("stripe", config(), "Stripe", null),
                request);
    }

    private static WebhookRequest request(final String payload, final String signature) {
        final Map<String, List<String>> headers = signature == null
                ? Map.of()
                : Map.of("stripe-signature", List.of(signature));
        return new WebhookRequest("stripe", payload, headers, Map.of());
    }

    private static Map<String, String> config() {
        return Map.of(
                "secretKey", "sk_test_secret",
                "webhookSecret", WEBHOOK_SECRET);
    }

    private static String payload(final UUID transactionId, final String paymentStatus) {
        return """
                {"id":"evt_test","object":"event","type":"checkout.session.completed",
                 "data":{"object":{"id":"cs_test","object":"checkout.session",
                 "payment_status":"%s","status":"complete","payment_intent":"pi_test",
                 "metadata":{"paymentTransactionId":"%s"}}}}
                """.formatted(paymentStatus, transactionId);
    }

    private static String signature(final String payload, final String secret) throws Exception {
        final long timestamp = Instant.now().getEpochSecond();
        final Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        final byte[] digest = mac.doFinal((timestamp + "." + payload).getBytes(StandardCharsets.UTF_8));
        return "t=" + timestamp + ",v1=" + HexFormat.of().formatHex(digest);
    }
}
