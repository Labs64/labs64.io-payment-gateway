package io.labs64.paymentgateway.service;

import java.util.List;

import io.labs64.paymentgateway.v1.model.PaymentMethod;
import io.labs64.paymentgateway.v1.model.PspConfigRequest;
import io.labs64.paymentgateway.v1.model.PspConfigResponse;

/**
 * Service for managing payment methods and tenant PSP configurations.
 */
public interface PaymentMethodService {

    /**
     * Retrieve available payment methods for a tenant, optionally filtered by currency and country.
     *
     * @param tenantId tenant identifier
     * @param currency optional ISO-4217 currency filter
     * @param country  optional ISO-3166-1 alpha-2 country filter
     * @return list of available payment methods
     */
    List<PaymentMethod> getPaymentMethods(String tenantId, String currency, String country);

    /**
     * Configure PSP settings for a tenant.
     *
     * @param tenantId        tenant identifier
     * @param paymentMethodId payment method to configure
     * @param request         PSP configuration request
     */
    void configurePsp(String tenantId, String paymentMethodId, PspConfigRequest request);

    /**
     * Retrieve PSP configuration for a tenant. Sensitive values are masked.
     *
     * @param tenantId        tenant identifier
     * @param paymentMethodId payment method identifier
     * @return PSP configuration response with masked sensitive fields
     */
    PspConfigResponse getPspConfig(String tenantId, String paymentMethodId);
}
