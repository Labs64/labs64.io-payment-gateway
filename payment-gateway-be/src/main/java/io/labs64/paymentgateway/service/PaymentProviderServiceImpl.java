package io.labs64.paymentgateway.service;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import io.labs64.paymentgateway.exception.ConflictException;
import io.labs64.paymentgateway.exception.ValidationException;
import io.labs64.paymentgateway.message.PaymentProviderMessages;
import io.labs64.paymentgateway.psp.internal.PaymentProviderRegistry;
import io.labs64.paymentgateway.psp.spi.PaymentProvider;
import io.labs64.paymentgateway.psp.spi.ProviderConfigField;
import io.labs64.paymentgateway.psp.spi.ProviderConfigSupport;
import io.labs64.paymentgateway.service.filter.PaymentProviderFilter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.labs64.paymentgateway.config.PaymentGatewayProperties.PaymentDefinition;
import io.labs64.paymentgateway.entity.PaymentProviderEntity;
import io.labs64.paymentgateway.exception.NotFoundException;
import io.labs64.paymentgateway.repository.PaymentProviderRepository;
import io.labs64.paymentgateway.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;

/**
 * Default {@link PaymentProviderService} implementation.
 * <p>
 * Provider availability comes from {@link PaymentDefinitionService}; tenant
 * ownership, active state, display overrides, and PSP configuration come from
 * {@link PaymentProviderEntity} records. DTO shaping and config masking
 * intentionally belong to mapper/controller layers.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentProviderServiceImpl implements PaymentProviderService {
    private final PaymentDefinitionService paymentDefinitionService;
    private final PaymentProviderRepository repository;
    private final PaymentRepository paymentRepository;
    private final PaymentProviderMessages msg;
    private final PaymentProviderRegistry paymentProviders;

    @Override
    @Transactional(readOnly = true)
    public Optional<PaymentProviderEntity> find(final String tenantId, final String provider) {
        log.debug("Find payment provider for tenantId={}, provider={}", tenantId, provider);

        if (paymentDefinitionService.findEnabled(provider).isEmpty()) {
            return Optional.empty();
        }

        return repository.findByTenantIdAndProvider(tenantId, provider);
    }

    @Override
    @Transactional(readOnly = true)
    public PaymentProviderEntity get(final String tenantId, final String provider) {
        log.debug("Get payment provider for tenantId={}, provider={}", tenantId, provider);
        return find(tenantId, provider).orElseThrow(() -> new NotFoundException(msg.notFound(provider)));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<PaymentProviderEntity> list(final String tenantId, final PaymentProviderFilter filter, final Pageable pageable) {
        log.debug("Loading payment providers for tenantId={}, filter={}",
                tenantId, filter);

        final Set<String> providers = paymentDefinitionService.listEnabled()
                .stream()
                .filter(pmc -> {
                    final boolean countryMatches = filter.isCountryBlank() || pmc.getSupportedCountries().contains(filter.country());
                    final boolean currencyMatches = filter.isCurrencyBlank() || pmc.getSupportedCurrencies().contains(filter.currency());

                    return countryMatches && currencyMatches;
                })
                .map(PaymentDefinition::getProvider)
                .collect(Collectors.toSet());

        if (providers.isEmpty()) {
            return Page.empty(pageable);
        }

        final Page<PaymentProviderEntity> result = repository.searchByTenantIdAndProviders(tenantId, providers, filter.active(), pageable);
        log.debug("Found {} payment providers for tenantId={}", result.getTotalElements(), tenantId);

        return result;
    }

    @Override
    @Transactional
    public PaymentProviderEntity create(final String tenantId, final String provider,
                                        final PaymentProviderEntity entity) {
        log.info("Creating tenant payment provider for tenantId={}, provider={}", tenantId, provider);

        final PaymentDefinition providerDefinition = paymentDefinitionService.findEnabled(provider)
                .orElseThrow(() -> new NotFoundException(msg.notSupported(provider)));

        if (repository.existsByTenantIdAndProvider(tenantId, provider)) {
            throw new ConflictException(msg.alreadyExists(provider));
        }
        final Map<String, String> config = sanitizeConfig(provider, entity.getConfig());
        entity.setConfig(config);

        entity.setTenantId(tenantId);
        entity.setProvider(provider);

        if (StringUtils.isBlank(entity.getName())) {
            entity.setName(providerDefinition.getName());
        }

        if (entity.getDescription() == null) {
            entity.setDescription(providerDefinition.getDescription());
        }

        final PaymentProviderEntity saved = repository.save(entity);
        log.info("Payment provider saved for tenantId={}, provider={}, entity={}", tenantId, provider, saved);

        return saved;
    }

    @Override
    @Transactional
    public PaymentProviderEntity update(final String tenantId, final String provider,
                                        final Consumer<PaymentProviderEntity> updater) {
        log.info("Updating payment provider for tenantId={}, provider={}", tenantId, provider);

        paymentDefinitionService.findEnabled(provider)
                .orElseThrow(() -> new NotFoundException(msg.notSupported(provider)));

        return find(tenantId, provider)
                .map(entity -> {
                    updater.accept(entity);

                    final Map<String, String> config = sanitizeConfig(provider, entity.getConfig());
                    entity.setConfig(config);

                    log.debug("Update payment provider: {}", entity);
                    return entity;
                })
                .orElseThrow(() -> new NotFoundException(msg.notFound(provider)));
    }

    @Override
    @Transactional
    public boolean delete(final String tenantId, final String provider) {
        log.info("Deleting payment provider for tenantId={}, provider={}", tenantId, provider);

        final Optional<PaymentProviderEntity> paymentProvider = repository.findByTenantIdAndProvider(tenantId, provider);
        if (paymentProvider.isEmpty()) {
            return false;
        }

        if (paymentRepository.existsByTenantIdAndPaymentProviderId(tenantId, paymentProvider.get().getId())) {
            throw new ConflictException(msg.cannotDeleteWithPayments(provider));
        }

        final int affected = repository.deleteByTenantIdAndProvider(tenantId, provider);
        log.debug("Delete payment provider provider={} tenant={} affected={}", provider, tenantId, affected);
        return affected > 0;
    }

    private Map<String, String> sanitizeConfig(final String provider, final Map<String, String> rawConfig) {
        final Map<String, String> source = Optional.ofNullable(rawConfig).orElseGet(Collections::emptyMap);
        final PaymentProvider paymentProvider = paymentProviders.getProvider(provider);

        if (!(paymentProvider instanceof ProviderConfigSupport configSupport)) {
            if (source.isEmpty()) {
                return Map.of();
            }
            throw new ValidationException(msg.configNotSupported(provider));
        }

        final Set<ProviderConfigField> fields = configSupport.configFields();
        final Set<String> allowedNames = fields.stream()
                .map(ProviderConfigField::name)
                .collect(Collectors.toUnmodifiableSet());

        final Map<String, String> sanitized = source.entrySet().stream()
                .filter(entry -> allowedNames.contains(entry.getKey()))
                .filter(entry -> entry.getValue() != null)
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (_, right) -> right,
                        LinkedHashMap::new));

        fields.stream()
                .filter(ProviderConfigField::required)
                .filter(field -> StringUtils.isBlank(sanitized.get(field.name())))
                .findFirst()
                .ifPresent(field -> {
                    throw new ValidationException(msg.configFieldRequired(provider, field.name()));
                });

        configSupport.validateConfig(Collections.unmodifiableMap(sanitized));
        return sanitized;
    }
}
