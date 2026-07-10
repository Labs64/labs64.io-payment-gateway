package io.labs64.paymentgateway.controller;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import io.labs64.paymentgateway.entity.PaymentProviderEntity;
import io.labs64.paymentgateway.mapper.PaymentProviderMapper;
import io.labs64.paymentgateway.model.PaymentProvider;
import io.labs64.paymentgateway.model.PaymentProviderCreateRequest;
import io.labs64.paymentgateway.model.PaymentProviderListResponse;
import io.labs64.paymentgateway.model.PaymentProviderUpdateRequest;
import io.labs64.paymentgateway.model.Scopes;
import io.labs64.paymentgateway.service.PaymentProviderService;
import io.labs64.paymentgateway.service.filter.PaymentProviderFilter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import io.labs64.authcontext.core.AuthContext;
import io.labs64.authcontext.core.AuthContextHolder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentProviderControllerTest {

    private static final String TENANT_ID = "tenant-a";
    private static final String PROVIDER = "stripe";
    private static final UUID PAYMENT_PROVIDER_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440010");

    @Mock
    private PaymentProviderService service;

    @Mock
    private PaymentProviderMapper mapper;

    @InjectMocks
    private PaymentProviderController controller;

    @BeforeEach
    void setUp() {
        authenticate(Scopes.PAYMENT_PROVIDER_READ.getValue());
    }

    @AfterEach
    void tearDown() {
        AuthContextHolder.clear();
    }

    @Test
    void getPaymentProviderUsesTenantAndAlwaysIncludesConfigWhenScopeAllowsIt() {
        final PaymentProviderEntity entity = entity();
        final PaymentProvider dto = dto(PROVIDER);
        when(service.get(TENANT_ID, PAYMENT_PROVIDER_ID)).thenReturn(entity);
        when(mapper.toDtoWithConfig(entity)).thenReturn(dto);

        final ResponseEntity<PaymentProvider> result = controller.getPaymentProvider(PAYMENT_PROVIDER_ID);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isSameAs(dto);
        verify(service).get(TENANT_ID, PAYMENT_PROVIDER_ID);
        verify(mapper).toDtoWithConfig(entity);
    }

    @Test
    void listPaymentProvidersBuildsFilterAndMapsPage() {
        final PageImpl<PaymentProviderEntity> page = new PageImpl<>(List.of(entity()));
        final PaymentProviderListResponse response = new PaymentProviderListResponse();
        when(service.list(eq(TENANT_ID), any(PaymentProviderFilter.class), eq(Pageable.unpaged()))).thenReturn(page);
        when(mapper.toPage(page)).thenReturn(response);

        final ResponseEntity<PaymentProviderListResponse> result = controller.listPaymentProviders(
                "USD",
                "US",
                true);

        final ArgumentCaptor<PaymentProviderFilter> filterCaptor = ArgumentCaptor.forClass(PaymentProviderFilter.class);
        verify(service).list(eq(TENANT_ID), filterCaptor.capture(), eq(Pageable.unpaged()));
        assertThat(filterCaptor.getValue().currency()).isEqualTo("USD");
        assertThat(filterCaptor.getValue().country()).isEqualTo("US");
        assertThat(filterCaptor.getValue().active()).isTrue();
        assertThat(result.getBody()).isSameAs(response);
    }

    @Test
    void createPaymentProviderPassesMappedEntityWithProviderFromRequest() {
        final PaymentProviderCreateRequest request = new PaymentProviderCreateRequest(PROVIDER, true);
        request.setConfig(Map.of("apiKey", "secret"));
        final PaymentProviderEntity mapped = PaymentProviderEntity.builder()
                .provider(PROVIDER)
                .active(true)
                .config(Map.of("apiKey", "secret"))
                .build();
        final PaymentProviderEntity saved = entity();
        final PaymentProvider dto = dto(PROVIDER);

        when(mapper.toEntity(request)).thenReturn(mapped);
        when(service.create(TENANT_ID, mapped)).thenReturn(saved);
        when(mapper.toDtoWithConfig(saved)).thenReturn(dto);

        final ResponseEntity<PaymentProvider> result = controller.createPaymentProvider(request);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isSameAs(dto);
        verify(service).create(TENANT_ID, mapped);
    }

    @Test
    void updatePaymentProviderAppliesMapperUpdaterInsideServiceCall() {
        final PaymentProviderUpdateRequest request = new PaymentProviderUpdateRequest().active(false);
        final PaymentProviderEntity saved = entity();
        final PaymentProvider dto = dto(PROVIDER);

        when(service.update(eq(TENANT_ID), eq(PAYMENT_PROVIDER_ID), any())).thenAnswer(invocation -> {
            final java.util.function.Consumer<PaymentProviderEntity> updater = invocation.getArgument(2);
            updater.accept(saved);
            return saved;
        });
        when(mapper.toDtoWithConfig(saved)).thenReturn(dto);

        final ResponseEntity<PaymentProvider> result = controller.updatePaymentProvider(PAYMENT_PROVIDER_ID, request);

        assertThat(result.getBody()).isSameAs(dto);
        verify(mapper).updateEntity(request, saved);
        verify(service).update(eq(TENANT_ID), eq(PAYMENT_PROVIDER_ID), any());
    }

    @Test
    void deletePaymentProviderReturnsNoContentWhenDeleted() {
        when(service.delete(TENANT_ID, PAYMENT_PROVIDER_ID)).thenReturn(true);

        final ResponseEntity<Void> result = controller.deletePaymentProvider(PAYMENT_PROVIDER_ID);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    private static void authenticate(final String... scopes) {
        AuthContextHolder.set(
                new AuthContext("test-user", TENANT_ID, Set.of(scopes), "test-request-id"));
    }

    private static PaymentProviderEntity entity() {
        return PaymentProviderEntity.builder()
                .tenantId(TENANT_ID)
                .id(PAYMENT_PROVIDER_ID)
                .provider(PROVIDER)
                .active(true)
                .name("Stripe")
                .description("Cards")
                .config(Map.of("apiKey", "secret"))
                .build();
    }

    private static PaymentProvider dto(final String id) {
        final PaymentProvider dto = new PaymentProvider();
        dto.setId(PAYMENT_PROVIDER_ID);
        dto.setProvider(id);
        return dto;
    }
}
