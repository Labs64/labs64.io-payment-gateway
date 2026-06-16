package io.labs64.paymentgateway.security;

/**
 * Authenticated request principal used by the temporary dev authentication
 * filter and, later, by JWT authentication mapping.
 */
public record AuthPrincipal(String tenantId) {
}
