package io.labs64.paymentgateway.service;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import io.labs64.paymentgateway.config.PaymentGatewayProperties;
import io.labs64.paymentgateway.config.PaymentGatewayProperties.PaymentDefinition;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PaymentDefinitionService {
    private final PaymentGatewayProperties properties;

    public List<PaymentDefinition> list() {
        return properties.getPaymentDefinitions();
    }

    public List<PaymentDefinition> listEnabled() {
        return list().stream()
                .filter(PaymentDefinition::isEnabled)
                .toList();
    }

    public Optional<PaymentDefinition> find(final String provider) {
        return list().stream()
                .filter(settings -> Objects.equals(settings.getProvider(), provider))
                .findFirst();
    }

    public Optional<PaymentDefinition> findEnabled(final String provider) {
        return find(provider)
                .filter(PaymentDefinition::isEnabled);
    }
}
