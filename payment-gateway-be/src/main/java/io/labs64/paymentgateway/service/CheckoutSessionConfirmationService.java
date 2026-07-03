package io.labs64.paymentgateway.service;

import java.util.UUID;

import io.labs64.paymentgateway.entity.CheckoutSessionEntity;

/**
 * Service for public-safe checkout session confirmation views.
 */
public interface CheckoutSessionConfirmationService {

    /**
     * Gets checkout session data needed to build a public confirmation page.
     *
     * @param sessionId checkout session identifier
     * @return checkout session with linked payment and payment transaction
     */
    CheckoutSessionEntity get(UUID sessionId);
}
