package io.labs64.paymentgateway.controller;

import java.util.Set;

import io.labs64.paymentgateway.entity.PaymentProviderEntity;
import io.labs64.paymentgateway.exception.ForbiddenException;
import io.labs64.paymentgateway.exception.NotFoundException;
import io.labs64.paymentgateway.mapper.PaymentProviderMapper;
import io.labs64.paymentgateway.message.PaymentProviderMessages;
import io.labs64.paymentgateway.model.PaymentProviderCreateRequest;
import io.labs64.paymentgateway.model.PaymentProviderUpdateRequest;
import io.labs64.paymentgateway.security.AuthContextHolder;
import io.labs64.paymentgateway.security.Scopes;
import io.labs64.paymentgateway.service.filter.PaymentProviderFilter;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import io.labs64.paymentgateway.service.PaymentProviderService;
import io.labs64.paymentgateway.api.PaymentProvidersApi;
import io.labs64.paymentgateway.model.PaymentProvider;
import io.labs64.paymentgateway.model.PaymentProviderListResponse;
import lombok.RequiredArgsConstructor;

@Slf4j
@RestController
@RequiredArgsConstructor
public class PaymentProviderController implements PaymentProvidersApi {
    private final PaymentProviderService service;
    private final PaymentProviderMapper mapper;
    private final PaymentProviderMessages msg;

    @Override
    public ResponseEntity<PaymentProvider> getPaymentProvider(String provider, @Nullable Set<String> with) {
        final String tenantId = AuthContextHolder.require().tenantId();

        log.info("Payment provider get requested | tenantId={}, provider={}, with={}",
                tenantId, provider, with);

        requireConfigScope(with);

        final PaymentProviderEntity entity = service.get(tenantId, provider);
        final PaymentProvider response = mapper.toDto(entity, with);

        return ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<PaymentProviderListResponse> listPaymentProviders(
            @Nullable String currency,
            @Nullable String country,
            @Nullable Boolean active,
            @Nullable Set<String> with) {
        final String tenantId = AuthContextHolder.require().tenantId();

        log.info("Payment provider list requested | tenantId={}, currency={}, country={}, active={}, with={}",
                tenantId, currency, country, active, with);

        requireConfigScope(with);

        final PaymentProviderFilter filter = new PaymentProviderFilter(currency, country, active);

        final Page<PaymentProviderEntity> list = service.list(tenantId, filter, Pageable.unpaged());
        final PaymentProviderListResponse response = mapper.toPage(list, with);

        return ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<PaymentProvider> createPaymentProvider(String provider, PaymentProviderCreateRequest request) {
        final String tenantId = AuthContextHolder.require().tenantId();
        final Set<String> configKeys = request.getConfig() != null ? request.getConfig().keySet() : null;

        log.info("Payment provider create requested | tenantId={}, provider={}, active={}, configKeys={}",
                tenantId, provider, request.getActive(), configKeys);

        final PaymentProviderEntity entity = service.create(tenantId, provider, mapper.toEntity(request));
        final PaymentProvider response = mapper.toDto(entity, Set.of("config"));

        return ResponseEntity.ok().body(response);
    }

    @Override
    public ResponseEntity<PaymentProvider> updatePaymentProvider(
            final String provider,
            final PaymentProviderUpdateRequest request) {
        final String tenantId = AuthContextHolder.require().tenantId();
        final Set<String> configKeys = request.getConfig() != null ? request.getConfig().keySet() : null;

        log.info("Payment provider update requested | tenantId={}, provider={}, active={}, configKeys={}",
                tenantId, provider, request.getActive(), configKeys);

        final PaymentProviderEntity entity = service.update(tenantId, provider, (pm) -> mapper.updateEntity(request, pm));
        final PaymentProvider response = mapper.toDto(entity, Set.of("config"));

        return ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<Void> deletePaymentProvider(final String provider) {
        final String tenantId = AuthContextHolder.require().tenantId();

        log.info("Payment provider delete requested | tenantId={}, provider={}",
                tenantId, provider);

        if (!service.delete(tenantId, provider)) {
            throw new NotFoundException(msg.notFound(provider));
        }

        return ResponseEntity.noContent().build();
    }

    private void requireConfigScope(final Set<String> with) {
        if (with == null || !with.contains("config")) {
            return;
        }
        if (!AuthContextHolder.require().hasScope(Scopes.PAYMENT_PROVIDER_WRITE)) {
            throw new ForbiddenException(msg.configScopeRequired(Scopes.PAYMENT_PROVIDER_WRITE));
        }
    }
}
