package io.labs64.paymentgateway.psp.providers.paypal;

import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import io.labs64.paymentgateway.config.PaymentGatewayProperties;
import io.labs64.paymentgateway.exception.ValidationException;
import io.labs64.paymentgateway.model.NextAction;
import io.labs64.paymentgateway.model.PaymentTransactionStatus;
import io.labs64.paymentgateway.psp.spi.CheckoutPreparationContext;
import io.labs64.paymentgateway.psp.spi.CheckoutSession;
import io.labs64.paymentgateway.psp.spi.CheckoutSessionDraft;
import io.labs64.paymentgateway.psp.spi.PaymentExecutionRequest;
import io.labs64.paymentgateway.psp.spi.PaymentResult;
import io.labs64.paymentgateway.psp.spi.ProviderCheckoutContext;
import io.labs64.paymentgateway.psp.spi.ProviderConfigField;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaypalPaymentProviderTest {

    private final PaypalPaymentProvider provider = new PaypalPaymentProvider(new PaymentGatewayProperties());

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
                "environment", true));
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
                .isInstanceOf(ValidationException.class)
                .hasMessage("PayPal environment must be either sandbox or live.");
    }

    @Test
    void prepareCheckoutSessionStoresReturnAndCancelUrls() {
        final CheckoutPreparationContext context = new CheckoutPreparationContext(
                null,
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
                .isInstanceOf(ValidationException.class)
                .hasMessage("PayPal checkout requires returnUrl.");
    }

    @Test
    void prepareCheckoutSessionRequiresCancelUrl() {
        final CheckoutPreparationContext context = checkoutContext(Map.of(
                "returnUrl", "https://checkout.example.com/payment/return"));

        assertThatThrownBy(() -> provider.prepareCheckoutSession(context))
                .isInstanceOf(ValidationException.class)
                .hasMessage("PayPal checkout requires cancelUrl.");
    }

    @Test
    void prepareCheckoutSessionRequiresAbsoluteUrls() {
        final CheckoutPreparationContext context = checkoutContext(Map.of(
                "returnUrl", "/payment/return",
                "cancelUrl", "https://checkout.example.com/payment/cancel"));

        assertThatThrownBy(() -> provider.prepareCheckoutSession(context))
                .isInstanceOf(ValidationException.class)
                .hasMessage("PayPal checkout returnUrl must be an absolute URL.");
    }

    @Test
    void executeRequiresCheckoutSession() {
        assertThatThrownBy(() -> provider.execute(new io.labs64.paymentgateway.psp.spi.PaymentContext(null, null, null)))
                .isInstanceOf(ValidationException.class)
                .hasMessage("PayPal checkout session is required.");
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
        assertThat(result.nextAction().type()).isEqualTo(NextAction.TypeEnum.REDIRECT);
        assertThat(result.nextAction().details()).containsEntry("url", "https://checkout.example.com/payment/cancel");
    }

    private static Map<String, String> config(final String environment) {
        return Map.of(
                "clientId", "client-id",
                "clientSecret", "client-secret",
                "environment", environment);
    }

    private static CheckoutPreparationContext checkoutContext(final Map<String, Object> checkout) {
        return new CheckoutPreparationContext(null, null, null, new PaymentExecutionRequest(checkout));
    }
}
