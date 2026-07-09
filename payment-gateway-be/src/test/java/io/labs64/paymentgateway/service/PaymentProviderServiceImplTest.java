package io.labs64.paymentgateway.service;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import io.labs64.paymentgateway.config.PaymentGatewayProperties.PaymentDefinition;
import io.labs64.paymentgateway.entity.PaymentProviderEntity;
import io.labs64.paymentgateway.exception.ConflictException;
import io.labs64.paymentgateway.exception.NotFoundException;
import io.labs64.paymentgateway.exception.ValidationException;
import io.labs64.paymentgateway.message.PaymentProviderMessages;
import io.labs64.paymentgateway.psp.internal.PaymentProviderRegistry;
import io.labs64.paymentgateway.psp.spi.PaymentContext;
import io.labs64.paymentgateway.psp.spi.PaymentProvider;
import io.labs64.paymentgateway.psp.spi.PaymentResult;
import io.labs64.paymentgateway.psp.spi.ProviderConfigField;
import io.labs64.paymentgateway.psp.spi.ProviderConfigSupport;
import io.labs64.paymentgateway.repository.PaymentProviderRepository;
import io.labs64.paymentgateway.repository.PaymentRepository;
import io.labs64.paymentgateway.service.filter.PaymentProviderFilter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentProviderServiceImplTest {

    private static final String TENANT_ID = "tenant-a";
    private static final String PROVIDER = "stripe";
    private static final UUID PAYMENT_PROVIDER_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440010");

    @Mock
    private PaymentDefinitionService paymentDefinitionService;

    @Mock
    private PaymentProviderRepository repository;

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private PaymentProviderMessages msg;

    @Mock
    private PaymentProviderRegistry paymentProviders;

    private final ConfigurableProvider pspProvider = new ConfigurableProvider();

    @InjectMocks
    private PaymentProviderServiceImpl service;

    @Test
    void findReturnsEmptyWhenProviderDefinitionIsDisabledOrMissing() {
        when(repository.findByTenantIdAndId(TENANT_ID, PAYMENT_PROVIDER_ID)).thenReturn(Optional.of(entity(PROVIDER)));
        when(paymentDefinitionService.findEnabled(PROVIDER)).thenReturn(Optional.empty());

        assertThat(service.find(TENANT_ID, PAYMENT_PROVIDER_ID)).isEmpty();

        verify(repository).findByTenantIdAndId(TENANT_ID, PAYMENT_PROVIDER_ID);
        verify(paymentDefinitionService).findEnabled(PROVIDER);
    }

    @Test
    void findReturnsTenantPaymentProviderWhenDefinitionIsEnabled() {
        final PaymentProviderEntity entity = entity(PROVIDER);
        entity.setId(PAYMENT_PROVIDER_ID);
        when(repository.findByTenantIdAndId(TENANT_ID, PAYMENT_PROVIDER_ID)).thenReturn(Optional.of(entity));
        when(paymentDefinitionService.findEnabled(PROVIDER)).thenReturn(Optional.of(definition(PROVIDER)));

        assertThat(service.find(TENANT_ID, PAYMENT_PROVIDER_ID)).containsSame(entity);
    }

    @Test
    void listFiltersDefinitionsBeforeRepositorySearch() {
        final PaymentProviderEntity entity = entity(PROVIDER);
        when(paymentDefinitionService.listEnabled()).thenReturn(List.of(
                definition(PROVIDER, List.of("USD"), List.of("US")),
                definition("paypal", List.of("EUR"), List.of("DE"))));
        when(repository.searchByTenantIdAndProviders(
                TENANT_ID,
                Set.of(PROVIDER),
                true,
                Pageable.unpaged())).thenReturn(new PageImpl<>(List.of(entity)));

        final Page<PaymentProviderEntity> result = service.list(
                TENANT_ID,
                new PaymentProviderFilter("USD", "US", true),
                Pageable.unpaged());

        assertThat(result.getContent()).containsExactly(entity);
    }

    @Test
    void createAssignsTenantAndProviderAppliesDefaultsAndFiltersConfig() {
        final PaymentProviderEntity input = PaymentProviderEntity.builder()
                .active(true)
                .provider(PROVIDER)
                .config(Map.of("apiKey", "raw", "extra", "trash"))
                .build();
        final PaymentDefinition definition = definition(PROVIDER);

        when(paymentDefinitionService.findEnabled(PROVIDER)).thenReturn(Optional.of(definition));
        when(paymentProviders.getProvider(PROVIDER)).thenReturn(pspProvider);
        when(repository.save(any(PaymentProviderEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        final PaymentProviderEntity result = service.create(TENANT_ID, input);

        assertThat(result.getTenantId()).isEqualTo(TENANT_ID);
        assertThat(result.getProvider()).isEqualTo(PROVIDER);
        assertThat(result.getName()).isEqualTo("Stripe");
        assertThat(result.getDescription()).isEqualTo("Cards");
        assertThat(result.getConfig()).containsExactly(Map.entry("apiKey", "raw"));
        verify(repository).save(input);
    }

    @Test
    void createKeepsTenantDisplayOverrides() {
        final PaymentProviderEntity input = PaymentProviderEntity.builder()
                .active(true)
                .provider(PROVIDER)
                .name("My Stripe")
                .description("")
                .config(Map.of("apiKey", "raw"))
                .build();

        when(paymentDefinitionService.findEnabled(PROVIDER)).thenReturn(Optional.of(definition(PROVIDER)));
        when(paymentProviders.getProvider(PROVIDER)).thenReturn(pspProvider);
        when(repository.save(any(PaymentProviderEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        final PaymentProviderEntity result = service.create(TENANT_ID, input);

        assertThat(result.getName()).isEqualTo("My Stripe");
        assertThat(result.getDescription()).isEmpty();
    }

    @Test
    void updateAppliesUpdaterAndSanitizesMergedConfig() {
        final PaymentProviderEntity entity = entity(PROVIDER);
        entity.setId(PAYMENT_PROVIDER_ID);
        when(repository.findByTenantIdAndId(TENANT_ID, PAYMENT_PROVIDER_ID)).thenReturn(Optional.of(entity));
        when(paymentDefinitionService.findEnabled(PROVIDER)).thenReturn(Optional.of(definition(PROVIDER)));
        when(paymentProviders.getProvider(PROVIDER)).thenReturn(pspProvider);

        final PaymentProviderEntity result = service.update(TENANT_ID, PAYMENT_PROVIDER_ID, target -> {
            target.setActive(false);
            target.getConfig().put("webhookSecret", "raw-secret");
        });

        assertThat(result.isActive()).isFalse();
        assertThat(result.getConfig())
                .containsEntry("apiKey", "raw")
                .containsEntry("webhookSecret", "raw-secret");
    }

    @Test
    void createThrowsWhenProviderDoesNotAcceptConfig() {
        final PaymentProviderEntity input = PaymentProviderEntity.builder()
                .active(true)
                .provider(PROVIDER)
                .config(Map.of("apiKey", "raw"))
                .build();

        when(paymentDefinitionService.findEnabled(PROVIDER)).thenReturn(Optional.of(definition(PROVIDER)));
        when(paymentProviders.getProvider(PROVIDER)).thenReturn(new NonConfigurableProvider());
        when(msg.configNotSupported(PROVIDER)).thenReturn("config not supported");

        assertThatThrownBy(() -> service.create(TENANT_ID, input))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void createThrowsWhenRequiredConfigFieldIsMissing() {
        final PaymentProviderEntity input = PaymentProviderEntity.builder()
                .active(true)
                .provider(PROVIDER)
                .config(Map.of("extra", "trash"))
                .build();

        when(paymentDefinitionService.findEnabled(PROVIDER)).thenReturn(Optional.of(definition(PROVIDER)));
        when(paymentProviders.getProvider(PROVIDER)).thenReturn(pspProvider);
        when(msg.configFieldRequired(PROVIDER, "apiKey")).thenReturn("apiKey required");

        assertThatThrownBy(() -> service.create(TENANT_ID, input))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void deleteThrowsWhenPaymentProviderIsLinkedToPayments() {
        final PaymentProviderEntity entity = entity(PROVIDER);
        entity.setId(PAYMENT_PROVIDER_ID);
        when(repository.findByTenantIdAndId(TENANT_ID, PAYMENT_PROVIDER_ID)).thenReturn(Optional.of(entity));
        when(paymentRepository.existsByTenantIdAndPaymentProviderId(TENANT_ID, entity.getId())).thenReturn(true);
        when(msg.cannotDeleteWithPayments(PROVIDER)).thenReturn("linked");

        assertThatThrownBy(() -> service.delete(TENANT_ID, PAYMENT_PROVIDER_ID))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void deleteReturnsFalseWhenPaymentProviderDoesNotExist() {
        when(repository.findByTenantIdAndId(TENANT_ID, PAYMENT_PROVIDER_ID)).thenReturn(Optional.empty());

        assertThat(service.delete(TENANT_ID, PAYMENT_PROVIDER_ID)).isFalse();
    }

    @Test
    void deleteReturnsTrueWhenRepositoryDeletesRecord() {
        final PaymentProviderEntity entity = entity(PROVIDER);
        entity.setId(PAYMENT_PROVIDER_ID);
        when(repository.findByTenantIdAndId(TENANT_ID, PAYMENT_PROVIDER_ID)).thenReturn(Optional.of(entity));
        when(paymentRepository.existsByTenantIdAndPaymentProviderId(TENANT_ID, entity.getId())).thenReturn(false);
        when(repository.deleteByTenantIdAndId(TENANT_ID, PAYMENT_PROVIDER_ID)).thenReturn(1);

        assertThat(service.delete(TENANT_ID, PAYMENT_PROVIDER_ID)).isTrue();
    }

    @Test
    void getThrowsWhenPaymentProviderIsNotFound() {
        when(repository.findByTenantIdAndId(TENANT_ID, PAYMENT_PROVIDER_ID)).thenReturn(Optional.empty());
        when(msg.notFound(PAYMENT_PROVIDER_ID.toString())).thenReturn("not found");

        assertThatThrownBy(() -> service.get(TENANT_ID, PAYMENT_PROVIDER_ID))
                .isInstanceOf(NotFoundException.class);
    }

    private static PaymentProviderEntity entity(final String provider) {
        return PaymentProviderEntity.builder()
                .tenantId(TENANT_ID)
                .id(PAYMENT_PROVIDER_ID)
                .provider(provider)
                .active(true)
                .name("Stripe")
                .description("Cards")
                .config(new java.util.LinkedHashMap<>(Map.of("apiKey", "raw")))
                .build();
    }

    private static PaymentDefinition definition(final String provider) {
        return definition(provider, List.of("USD", "EUR"), List.of("US", "DE"));
    }

    private static PaymentDefinition definition(
            final String provider,
            final List<String> currencies,
            final List<String> countries) {
        final PaymentDefinition definition = new PaymentDefinition();
        definition.setProvider(provider);
        definition.setEnabled(true);
        definition.setName(provider.equals(PROVIDER) ? "Stripe" : provider);
        definition.setDescription(provider.equals(PROVIDER) ? "Cards" : provider);
        definition.setSupportedCurrencies(currencies);
        definition.setSupportedCountries(countries);
        return definition;
    }

    private static class ConfigurableProvider extends NonConfigurableProvider implements ProviderConfigSupport {

        @Override
        public Set<ProviderConfigField> configFields() {
            return Set.of(
                    ProviderConfigField.required("apiKey"),
                    ProviderConfigField.optional("webhookSecret"));
        }

        @Override
        public void validateConfig(final Map<String, String> config) {
            // Provider-specific validation hook; core already filtered unknown keys and required fields.
        }
    }

    private static class NonConfigurableProvider implements PaymentProvider {

        @Override
        public String provider() {
            return PROVIDER;
        }

        @Override
        public PaymentResult execute(final PaymentContext context) {
            return null;
        }
    }
}
