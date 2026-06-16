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
import io.labs64.paymentgateway.message.PaymentProviderMessages;
import io.labs64.paymentgateway.psp.internal.PaymentProviderRegistry;
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
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentProviderServiceImplTest {

    private static final String TENANT_ID = "tenant-a";
    private static final String PROVIDER = "stripe";

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

    @Mock
    private io.labs64.paymentgateway.psp.spi.PaymentProvider pspProvider;

    @InjectMocks
    private PaymentProviderServiceImpl service;

    @Test
    void findReturnsEmptyWhenProviderDefinitionIsDisabledOrMissing() {
        when(paymentDefinitionService.findEnabled(PROVIDER)).thenReturn(Optional.empty());

        assertThat(service.find(TENANT_ID, PROVIDER)).isEmpty();

        verify(paymentDefinitionService).findEnabled(PROVIDER);
        verifyNoMoreInteractions(repository);
    }

    @Test
    void findReturnsTenantPaymentProviderWhenDefinitionIsEnabled() {
        final PaymentProviderEntity entity = entity(PROVIDER);
        when(paymentDefinitionService.findEnabled(PROVIDER)).thenReturn(Optional.of(definition(PROVIDER)));
        when(repository.findByTenantIdAndProvider(TENANT_ID, PROVIDER)).thenReturn(Optional.of(entity));

        assertThat(service.find(TENANT_ID, PROVIDER)).containsSame(entity);
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
    void createAssignsTenantAndProviderAppliesDefaultsAndSanitizesConfig() {
        final PaymentProviderEntity input = PaymentProviderEntity.builder()
                .active(true)
                .config(Map.of("apiKey", "raw", "extra", "trash"))
                .build();
        final PaymentDefinition definition = definition(PROVIDER);

        when(paymentDefinitionService.findEnabled(PROVIDER)).thenReturn(Optional.of(definition));
        when(repository.existsByTenantIdAndProvider(TENANT_ID, PROVIDER)).thenReturn(false);
        when(paymentProviders.getProvider(PROVIDER)).thenReturn(pspProvider);
        when(pspProvider.validateAndSanitizePaymentProviderConfig(input.getConfig()))
                .thenReturn(Map.of("apiKey", "sanitized"));
        when(repository.save(any(PaymentProviderEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        final PaymentProviderEntity result = service.create(TENANT_ID, PROVIDER, input);

        assertThat(result.getTenantId()).isEqualTo(TENANT_ID);
        assertThat(result.getProvider()).isEqualTo(PROVIDER);
        assertThat(result.getName()).isEqualTo("Stripe");
        assertThat(result.getDescription()).isEqualTo("Cards");
        assertThat(result.getConfig()).containsExactly(Map.entry("apiKey", "sanitized"));
        verify(repository).save(input);
    }

    @Test
    void createKeepsTenantDisplayOverrides() {
        final PaymentProviderEntity input = PaymentProviderEntity.builder()
                .active(true)
                .name("My Stripe")
                .description("")
                .config(Map.of("apiKey", "raw"))
                .build();

        when(paymentDefinitionService.findEnabled(PROVIDER)).thenReturn(Optional.of(definition(PROVIDER)));
        when(repository.existsByTenantIdAndProvider(TENANT_ID, PROVIDER)).thenReturn(false);
        when(paymentProviders.getProvider(PROVIDER)).thenReturn(pspProvider);
        when(pspProvider.validateAndSanitizePaymentProviderConfig(input.getConfig())).thenReturn(input.getConfig());
        when(repository.save(any(PaymentProviderEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        final PaymentProviderEntity result = service.create(TENANT_ID, PROVIDER, input);

        assertThat(result.getName()).isEqualTo("My Stripe");
        assertThat(result.getDescription()).isEmpty();
    }

    @Test
    void createThrowsWhenProviderAlreadyExists() {
        when(paymentDefinitionService.findEnabled(PROVIDER)).thenReturn(Optional.of(definition(PROVIDER)));
        when(repository.existsByTenantIdAndProvider(TENANT_ID, PROVIDER)).thenReturn(true);
        when(msg.alreadyExists(PROVIDER)).thenReturn("exists");

        assertThatThrownBy(() -> service.create(TENANT_ID, PROVIDER, entity(PROVIDER)))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void updateAppliesUpdaterAndSanitizesMergedConfig() {
        final PaymentProviderEntity entity = entity(PROVIDER);
        when(paymentDefinitionService.findEnabled(PROVIDER)).thenReturn(Optional.of(definition(PROVIDER)));
        when(repository.findByTenantIdAndProvider(TENANT_ID, PROVIDER)).thenReturn(Optional.of(entity));
        when(paymentProviders.getProvider(PROVIDER)).thenReturn(pspProvider);
        when(pspProvider.validateAndSanitizePaymentProviderConfig(any()))
                .thenReturn(Map.of("apiKey", "sanitized", "webhookSecret", "sanitized-secret"));

        final PaymentProviderEntity result = service.update(TENANT_ID, PROVIDER, target -> {
            target.setActive(false);
            target.getConfig().put("webhookSecret", "raw-secret");
        });

        assertThat(result.isActive()).isFalse();
        assertThat(result.getConfig())
                .containsEntry("apiKey", "sanitized")
                .containsEntry("webhookSecret", "sanitized-secret");
    }

    @Test
    void deleteThrowsWhenPaymentProviderIsLinkedToPayments() {
        final PaymentProviderEntity entity = entity(PROVIDER);
        entity.setId(UUID.randomUUID());
        when(repository.findByTenantIdAndProvider(TENANT_ID, PROVIDER)).thenReturn(Optional.of(entity));
        when(paymentRepository.existsByTenantIdAndPaymentProviderId(TENANT_ID, entity.getId())).thenReturn(true);
        when(msg.cannotDeleteWithPayments(PROVIDER)).thenReturn("linked");

        assertThatThrownBy(() -> service.delete(TENANT_ID, PROVIDER))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void deleteReturnsFalseWhenPaymentProviderDoesNotExist() {
        when(repository.findByTenantIdAndProvider(TENANT_ID, PROVIDER)).thenReturn(Optional.empty());

        assertThat(service.delete(TENANT_ID, PROVIDER)).isFalse();
    }

    @Test
    void deleteReturnsTrueWhenRepositoryDeletesRecord() {
        final PaymentProviderEntity entity = entity(PROVIDER);
        entity.setId(UUID.randomUUID());
        when(repository.findByTenantIdAndProvider(TENANT_ID, PROVIDER)).thenReturn(Optional.of(entity));
        when(paymentRepository.existsByTenantIdAndPaymentProviderId(TENANT_ID, entity.getId())).thenReturn(false);
        when(repository.deleteByTenantIdAndProvider(TENANT_ID, PROVIDER)).thenReturn(1);

        assertThat(service.delete(TENANT_ID, PROVIDER)).isTrue();
    }

    @Test
    void getThrowsWhenPaymentProviderIsNotFound() {
        when(paymentDefinitionService.findEnabled(PROVIDER)).thenReturn(Optional.empty());
        when(msg.notFound(PROVIDER)).thenReturn("not found");

        assertThatThrownBy(() -> service.get(TENANT_ID, PROVIDER))
                .isInstanceOf(NotFoundException.class);
    }

    private static PaymentProviderEntity entity(final String provider) {
        return PaymentProviderEntity.builder()
                .tenantId(TENANT_ID)
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
}
