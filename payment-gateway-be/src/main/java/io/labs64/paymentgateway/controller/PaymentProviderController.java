package io.labs64.paymentgateway.controller;

import java.util.Set;
import java.util.UUID;

import io.labs64.paymentgateway.entity.PaymentProviderEntity;
import io.labs64.paymentgateway.exception.NotFoundException;
import io.labs64.paymentgateway.mapper.PaymentProviderMapper;
import io.labs64.paymentgateway.message.PaymentProviderMessages;
import io.labs64.paymentgateway.model.PaymentProviderCreateRequest;
import io.labs64.paymentgateway.model.PaymentProviderUpdateRequest;
import io.labs64.authcontext.core.AuthContextHolder;
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
    public ResponseEntity<PaymentProvider> getPaymentProvider(final UUID paymentProviderId) {
        final String tenantId = AuthContextHolder.require().tenantId();

        log.info("Payment provider get requested | tenantId={}, paymentProviderId={}", tenantId, paymentProviderId);

        final PaymentProviderEntity entity = service.get(tenantId, paymentProviderId);
        final PaymentProvider response = mapper.toDtoWithConfig(entity);

        return ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<PaymentProviderListResponse> listPaymentProviders(
            @Nullable String currency,
            @Nullable String country,
            @Nullable Boolean active) {
        final String tenantId = AuthContextHolder.require().tenantId();

        log.info("Payment provider list requested | tenantId={}, currency={}, country={}, active={}",
                tenantId, currency, country, active);

        final PaymentProviderFilter filter = new PaymentProviderFilter(currency, country, active);

        final Page<PaymentProviderEntity> list = service.list(tenantId, filter, Pageable.unpaged());
        final PaymentProviderListResponse response = mapper.toPage(list);

        return ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<PaymentProvider> createPaymentProvider(final PaymentProviderCreateRequest request) {
        final String tenantId = AuthContextHolder.require().tenantId();
        final Set<String> configKeys = request.getConfig() != null ? request.getConfig().keySet() : null;

        log.info("Payment provider create requested | tenantId={}, provider={}, active={}, configKeys={}",
                tenantId, request.getProvider(), request.getActive(), configKeys);

        final PaymentProviderEntity entity = service.create(tenantId, mapper.toEntity(request));
        final PaymentProvider response = mapper.toDtoWithConfig(entity);

        return ResponseEntity.ok().body(response);
    }

    @Override
    public ResponseEntity<PaymentProvider> updatePaymentProvider(
            final UUID paymentProviderId,
            final PaymentProviderUpdateRequest request) {
        final String tenantId = AuthContextHolder.require().tenantId();
        final Set<String> configKeys = request.getConfig() != null ? request.getConfig().keySet() : null;

        log.info("Payment provider update requested | tenantId={}, paymentProviderId={}, active={}, configKeys={}",
                tenantId, paymentProviderId, request.getActive(), configKeys);

        final PaymentProviderEntity entity = service.update(tenantId, paymentProviderId, (pm) -> mapper.updateEntity(request, pm));
        final PaymentProvider response = mapper.toDtoWithConfig(entity);

        return ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<Void> deletePaymentProvider(final UUID paymentProviderId) {
        final String tenantId = AuthContextHolder.require().tenantId();

        log.info("Payment provider delete requested | tenantId={}, paymentProviderId={}",
                tenantId, paymentProviderId);

        if (!service.delete(tenantId, paymentProviderId)) {
            throw new NotFoundException(msg.notFound(paymentProviderId.toString()));
        }

        return ResponseEntity.noContent().build();
    }
}
