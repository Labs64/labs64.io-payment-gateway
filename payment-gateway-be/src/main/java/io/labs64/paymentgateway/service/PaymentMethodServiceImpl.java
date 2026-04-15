package io.labs64.paymentgateway.service;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.labs64.paymentgateway.config.PaymentGatewayProperties;
import io.labs64.paymentgateway.config.PaymentGatewayProperties.PaymentMethodConfig;
import io.labs64.paymentgateway.entity.PspConfigEntity;
import io.labs64.paymentgateway.exception.NotFoundException;
import io.labs64.paymentgateway.exception.ValidationException;
import io.labs64.paymentgateway.mapper.PspConfigMapper;
import io.labs64.paymentgateway.psp.PaymentProviderRegistry;
import io.labs64.paymentgateway.repository.PspConfigRepository;
import io.labs64.paymentgateway.v1.model.PaymentMethod;
import io.labs64.paymentgateway.v1.model.PspConfigRequest;
import io.labs64.paymentgateway.v1.model.PspConfigResponse;
import lombok.RequiredArgsConstructor;

/**
 * Implementation of {@link PaymentMethodService}.
 * Loads payment methods from YAML config, combined with tenant-specific DB config.
 */
@Service
@RequiredArgsConstructor
public class PaymentMethodServiceImpl implements PaymentMethodService {

    private static final Logger log = LoggerFactory.getLogger(PaymentMethodServiceImpl.class);

    private final PaymentGatewayProperties properties;
    private final PspConfigRepository pspConfigRepository;
    private final PspConfigMapper pspConfigMapper;
    private final PaymentProviderRegistry providerRegistry;

    @Override
    @Transactional(readOnly = true)
    public List<PaymentMethod> getPaymentMethods(final String tenantId, final String currency, final String country) {
        log.debug("Loading payment methods for tenantId={}, currency={}, country={}", tenantId, currency, country);

        final List<PaymentMethod> result = new ArrayList<>();

        for (final PaymentMethodConfig config : properties.getPaymentMethods()) {
            if (!config.isEnabled()) {
                continue;
            }

            // Filter by currency
            if (currency != null && !currency.isBlank()
                    && !config.getSupportedCurrencies().contains(currency.toUpperCase())) {
                continue;
            }

            // Filter by country
            if (country != null && !country.isBlank()
                    && !config.getSupportedCountries().contains(country.toUpperCase())) {
                continue;
            }

            final PaymentMethod pm = new PaymentMethod();
            pm.setId(config.getId());
            pm.setName(config.getName());
            pm.setDescription(config.getDescription());
            pm.setRecurring(config.isRecurring());
            result.add(pm);
        }

        log.debug("Found {} payment methods for tenantId={}", result.size(), tenantId);
        return result;
    }

    @Override
    @Transactional
    public void configurePsp(final String tenantId, final String paymentMethodId, final PspConfigRequest request) {
        log.info("Configuring PSP for tenantId={}, paymentMethodId={}", tenantId, paymentMethodId);

        // Validate paymentMethodId exists in YAML config
        validatePaymentMethodId(paymentMethodId);

        if (request.getPspConfig() == null || request.getPspConfig().isEmpty()) {
            throw new ValidationException("PSP configuration cannot be empty.");
        }

        final PspConfigEntity entity = pspConfigRepository
                .findByTenantIdAndPaymentMethodId(tenantId, paymentMethodId)
                .map(existing -> {
                    existing.setConfig(request.getPspConfig());
                    existing.setEnabled(true);
                    return existing;
                })
                .orElseGet(() -> PspConfigEntity.builder()
                        .tenantId(tenantId)
                        .paymentMethodId(paymentMethodId)
                        .enabled(true)
                        .config(request.getPspConfig())
                        .build());

        pspConfigRepository.save(entity);
        log.info("PSP configuration saved for tenantId={}, paymentMethodId={}", tenantId, paymentMethodId);
    }

    @Override
    @Transactional(readOnly = true)
    public PspConfigResponse getPspConfig(final String tenantId, final String paymentMethodId) {
        log.debug("Loading PSP config for tenantId={}, paymentMethodId={}", tenantId, paymentMethodId);

        final PspConfigEntity entity = pspConfigRepository
                .findByTenantIdAndPaymentMethodId(tenantId, paymentMethodId)
                .orElseThrow(() -> new NotFoundException(
                        "PSP configuration not found for payment method '" + paymentMethodId + "'."));

        return pspConfigMapper.toResponse(entity);
    }

    private void validatePaymentMethodId(final String paymentMethodId) {
        final boolean exists = properties.getPaymentMethods().stream()
                .anyMatch(pm -> pm.getId().equals(paymentMethodId));
        if (!exists) {
            throw new ValidationException(
                    "Payment method '" + paymentMethodId + "' is not a valid/known payment method.");
        }
    }
}
