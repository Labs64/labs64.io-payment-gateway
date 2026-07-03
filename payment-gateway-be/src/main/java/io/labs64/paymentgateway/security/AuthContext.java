package io.labs64.paymentgateway.security;

import java.util.Set;

/**
 * Application-level authentication view.
 * <p>
 * This facade intentionally hides whether authentication data came from the
 * temporary development headers or from a verified JWT token.
 */
public record AuthContext(String tenantId, Set<String> scopes) {

    public boolean hasScope(final String scope) {
        return scopes != null && scopes.contains(scope);
    }

    public boolean hasAnyScope(final String... scopes) {
        if (scopes == null) {
            return false;
        }
        for (String scope : scopes) {
            if (hasScope(scope)) {
                return true;
            }
        }
        return false;
    }
}
