package io.labs64.paymentgateway.service;

import java.util.List;
import java.util.Optional;

import io.labs64.paymentgateway.config.PaymentGatewayProperties;
import io.labs64.paymentgateway.config.PaymentGatewayProperties.PaymentDefinition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentDefinitionServiceTest {

    private PaymentGatewayProperties properties;
    private PaymentDefinitionService service;

    @BeforeEach
    void setUp() {
        properties = new PaymentGatewayProperties();
        properties.setPaymentDefinitions(List.of(
                definition("stripe", true),
                definition("paypal", false),
                definition("noop", true)));
        service = new PaymentDefinitionService(properties);
    }

    @Test
    void listReturnsAllConfiguredPaymentDefinitions() {
        final List<PaymentDefinition> result = service.list();

        assertThat(result)
                .extracting(PaymentDefinition::getProvider)
                .containsExactly("stripe", "paypal", "noop");
    }

    @Test
    void listEnabledReturnsOnlyEnabledPaymentDefinitions() {
        final List<PaymentDefinition> result = service.listEnabled();

        assertThat(result)
                .extracting(PaymentDefinition::getProvider)
                .containsExactly("stripe", "noop");
    }

    @Test
    void findReturnsConfiguredPaymentDefinitionByProvider() {
        final Optional<PaymentDefinition> result = service.find("paypal");

        assertThat(result).isPresent();
        assertThat(result.get().getProvider()).isEqualTo("paypal");
        assertThat(result.get().isEnabled()).isFalse();
    }

    @Test
    void findReturnsEmptyWhenProviderIsUnknown() {
        assertThat(service.find("unknown")).isEmpty();
    }

    @Test
    void findEnabledReturnsConfiguredEnabledPaymentDefinition() {
        final Optional<PaymentDefinition> result = service.findEnabled("stripe");

        assertThat(result).isPresent();
        assertThat(result.get().getProvider()).isEqualTo("stripe");
    }

    @Test
    void findEnabledReturnsEmptyWhenPaymentDefinitionIsDisabled() {
        assertThat(service.findEnabled("paypal")).isEmpty();
    }

    private static PaymentDefinition definition(final String provider, final boolean enabled) {
        final PaymentDefinition definition = new PaymentDefinition();
        definition.setProvider(provider);
        definition.setEnabled(enabled);
        definition.setName(provider);
        definition.setDescription(provider + " description");
        definition.setRecurring(true);
        definition.setSupportedCurrencies(List.of("USD", "EUR"));
        definition.setSupportedCountries(List.of("US", "DE"));
        return definition;
    }
}
