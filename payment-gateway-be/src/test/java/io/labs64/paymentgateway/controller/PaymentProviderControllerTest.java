package io.labs64.paymentgateway.controller;

import java.util.List;
import java.util.Map;
import java.util.Set;

import io.labs64.paymentgateway.entity.PaymentProviderEntity;
import io.labs64.paymentgateway.exception.ForbiddenException;
import io.labs64.paymentgateway.mapper.PaymentProviderMapper;
import io.labs64.paymentgateway.message.PaymentProviderMessages;
import io.labs64.paymentgateway.model.PaymentProvider;
import io.labs64.paymentgateway.model.PaymentProviderCreateRequest;
import io.labs64.paymentgateway.model.PaymentProviderListResponse;
import io.labs64.paymentgateway.model.PaymentProviderUpdateRequest;
import io.labs64.paymentgateway.security.AuthPrincipal;
import io.labs64.paymentgateway.security.Scopes;
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
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentProviderControllerTest {

    private static final String TENANT_ID = "tenant-a";
    private static final String PROVIDER = "stripe";

    @Mock
    private PaymentProviderService service;

    @Mock
    private PaymentProviderMapper mapper;

    @Mock
    private PaymentProviderMessages msg;

    @InjectMocks
    private PaymentProviderController controller;

    @BeforeEach
    void setUp() {
        authenticate(Scopes.PAYMENT_PROVIDER_READ, Scopes.PAYMENT_PROVIDER_WRITE);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void getPaymentProviderUsesTenantAndIncludesConfigWhenScopeAllowsIt() {
        final PaymentProviderEntity entity = entity();
        final PaymentProvider dto = dto(PROVIDER);
        when(service.get(TENANT_ID, PROVIDER)).thenReturn(entity);
        when(mapper.toDto(entity, Set.of("config"))).thenReturn(dto);

        final ResponseEntity<PaymentProvider> result = controller.getPaymentProvider(PROVIDER, Set.of("config"));

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isSameAs(dto);
        verify(service).get(TENANT_ID, PROVIDER);
        verify(mapper).toDto(entity, Set.of("config"));
    }

    @Test
    void getPaymentProviderRejectsConfigIncludeWithoutWriteScope() {
        authenticate(Scopes.PAYMENT_PROVIDER_READ);
        when(msg.configScopeRequired(Scopes.PAYMENT_PROVIDER_WRITE)).thenReturn("scope required");

        assertThatThrownBy(() -> controller.getPaymentProvider(PROVIDER, Set.of("config")))
                .isInstanceOf(ForbiddenException.class);

        verifyNoInteractions(service, mapper);
    }

    @Test
    void listPaymentProvidersBuildsFilterAndMapsPage() {
        final PageImpl<PaymentProviderEntity> page = new PageImpl<>(List.of(entity()));
        final PaymentProviderListResponse response = new PaymentProviderListResponse();
        when(service.list(eq(TENANT_ID), any(PaymentProviderFilter.class), eq(Pageable.unpaged()))).thenReturn(page);
        when(mapper.toPage(page, Set.of("config"))).thenReturn(response);

        final ResponseEntity<PaymentProviderListResponse> result = controller.listPaymentProviders(
                "USD",
                "US",
                true,
                Set.of("config"));

        final ArgumentCaptor<PaymentProviderFilter> filterCaptor = ArgumentCaptor.forClass(PaymentProviderFilter.class);
        verify(service).list(eq(TENANT_ID), filterCaptor.capture(), eq(Pageable.unpaged()));
        assertThat(filterCaptor.getValue().currency()).isEqualTo("USD");
        assertThat(filterCaptor.getValue().country()).isEqualTo("US");
        assertThat(filterCaptor.getValue().active()).isTrue();
        assertThat(result.getBody()).isSameAs(response);
    }

    @Test
    void createPaymentProviderPassesPathProviderSeparatelyFromMappedEntity() {
        final PaymentProviderCreateRequest request = new PaymentProviderCreateRequest(true);
        request.setConfig(Map.of("apiKey", "secret"));
        final PaymentProviderEntity mapped = PaymentProviderEntity.builder()
                .active(true)
                .config(Map.of("apiKey", "secret"))
                .build();
        final PaymentProviderEntity saved = entity();
        final PaymentProvider dto = dto(PROVIDER);

        when(mapper.toEntity(request)).thenReturn(mapped);
        when(service.create(TENANT_ID, PROVIDER, mapped)).thenReturn(saved);
        when(mapper.toDto(saved, Set.of("config"))).thenReturn(dto);

        final ResponseEntity<PaymentProvider> result = controller.createPaymentProvider(PROVIDER, request);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isSameAs(dto);
        verify(service).create(TENANT_ID, PROVIDER, mapped);
    }

    @Test
    void updatePaymentProviderAppliesMapperUpdaterInsideServiceCall() {
        final PaymentProviderUpdateRequest request = new PaymentProviderUpdateRequest().active(false);
        final PaymentProviderEntity saved = entity();
        final PaymentProvider dto = dto(PROVIDER);

        when(service.update(eq(TENANT_ID), eq(PROVIDER), any())).thenAnswer(invocation -> {
            final java.util.function.Consumer<PaymentProviderEntity> updater = invocation.getArgument(2);
            updater.accept(saved);
            return saved;
        });
        when(mapper.toDto(saved, Set.of("config"))).thenReturn(dto);

        final ResponseEntity<PaymentProvider> result = controller.updatePaymentProvider(PROVIDER, request);

        assertThat(result.getBody()).isSameAs(dto);
        verify(mapper).updateEntity(request, saved);
        verify(service).update(eq(TENANT_ID), eq(PROVIDER), any());
    }

    @Test
    void deletePaymentProviderReturnsNoContentWhenDeleted() {
        when(service.delete(TENANT_ID, PROVIDER)).thenReturn(true);

        final ResponseEntity<Void> result = controller.deletePaymentProvider(PROVIDER);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    private static void authenticate(final String... scopes) {
        final List<SimpleGrantedAuthority> authorities = java.util.Arrays.stream(scopes)
                .map(scope -> new SimpleGrantedAuthority("SCOPE_" + scope))
                .toList();
        final TestingAuthenticationToken authentication = new TestingAuthenticationToken(
                new AuthPrincipal(TENANT_ID),
                "n/a",
                authorities);
        authentication.setAuthenticated(true);
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    private static PaymentProviderEntity entity() {
        return PaymentProviderEntity.builder()
                .tenantId(TENANT_ID)
                .provider(PROVIDER)
                .active(true)
                .name("Stripe")
                .description("Cards")
                .config(Map.of("apiKey", "secret"))
                .build();
    }

    private static PaymentProvider dto(final String id) {
        final PaymentProvider dto = new PaymentProvider();
        dto.setId(id);
        return dto;
    }
}
