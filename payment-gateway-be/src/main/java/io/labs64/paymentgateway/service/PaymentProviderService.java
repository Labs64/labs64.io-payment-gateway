package io.labs64.paymentgateway.service;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

import io.labs64.paymentgateway.entity.PaymentProviderEntity;
import io.labs64.paymentgateway.service.filter.PaymentProviderFilter;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Manages tenant-owned payment providers.
 * <p>
 * YAML configuration defines which providers are globally supported and enabled.
 * This service works with tenant-specific payment provider records stored in the
 * database: active state, display metadata, and PSP configuration.
 */
public interface PaymentProviderService {

    /**
     * Finds a tenant payment provider by id only when its provider is globally
     * supported and enabled in YAML.
     *
     * @param tenantId tenant identifier from the authenticated request context
     * @param paymentProviderId tenant payment provider identifier
     * @return tenant payment provider if both the YAML provider and tenant record exist
     */
    Optional<PaymentProviderEntity> find(String tenantId, UUID paymentProviderId);

    /**
     * Returns a tenant payment provider or raises a not-found API exception.
     *
     * @param tenantId tenant identifier from the authenticated request context
     * @param paymentProviderId tenant payment provider identifier
     * @return existing tenant payment provider
     */
    PaymentProviderEntity get(String tenantId, UUID paymentProviderId);

    /**
     * Lists tenant payment providers for providers allowed by YAML and optional
     * business filters.
     * <p>
     * The filter controls provider eligibility by country and currency, and
     * tenant payment provider visibility by active state. When
     * {@link PaymentProviderFilter#active()} is {@code null}, both active and
     * inactive tenant records are returned.
     *
     * @param tenantId tenant identifier from the authenticated request context
     * @param filter   non-null payment provider filters; blank country/currency
     *                 values are ignored
     * @param pageable page request for the result set
     * @return page of tenant payment providers matching the filters
     */
    Page<PaymentProviderEntity> list(
            String tenantId,
            PaymentProviderFilter filter,
            Pageable pageable);

    /**
     * Creates a tenant payment provider for a globally supported provider.
     * <p>
     * The caller supplies a prepared entity, usually produced by a mapper. The
     * service assigns tenant/provider ownership, applies YAML display defaults
     * where needed, and validates the required PSP configuration.
     *
     * @param tenantId tenant identifier from the authenticated request context
     * @param entity   tenant payment provider entity to persist
     * @return persisted tenant payment provider
     */
    PaymentProviderEntity create(String tenantId, PaymentProviderEntity entity);

    /**
     * Updates an existing tenant payment provider.
     * <p>
     * The updater is intentionally passed as business logic so controllers or
     * mappers can decide how request fields are applied without duplicating the
     * tenant/provider lookup and provider support checks.
     *
     * @param tenantId tenant identifier from the authenticated request context
     * @param paymentProviderId tenant payment provider identifier
     * @param updater  mutation callback applied to the managed entity
     * @return updated tenant payment provider
     */
    PaymentProviderEntity update(String tenantId, UUID paymentProviderId, Consumer<PaymentProviderEntity> updater);

    /**
     * Deletes a tenant payment provider by id.
     *
     * @param tenantId tenant identifier from the authenticated request context
     * @param paymentProviderId tenant payment provider identifier
     * @return {@code true} when a tenant record was deleted
     */
    boolean delete(String tenantId, UUID paymentProviderId);
}
