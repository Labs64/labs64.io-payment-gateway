package io.labs64.paymentgateway.security;

import java.util.Set;

/**
 * Application-level authentication view over the trusted gateway auth-context.
 * Roles come from the gateway-verified {@code X-Auth-Roles} header.
 */
public record AuthContext(String tenantId, Set<String> roles) {

    public boolean hasRole(final String role) {
        return roles != null && roles.contains(role);
    }

    public boolean hasAnyRole(final String... candidates) {
        if (candidates == null) {
            return false;
        }
        for (String role : candidates) {
            if (hasRole(role)) {
                return true;
            }
        }
        return false;
    }
}
