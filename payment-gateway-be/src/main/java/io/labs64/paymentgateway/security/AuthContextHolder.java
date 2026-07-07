package io.labs64.paymentgateway.security;

import java.util.Optional;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;

import io.labs64.authcontext.UserContextHolder;
import io.labs64.paymentgateway.exception.TenantRequiredException;

/**
 * Reads the current application auth context from the trusted gateway headers
 * ({@code l64-auth-context-spring-boot-starter}, RFC-03).
 *
 * <p>{@code labs64.tenant.default} (wired by {@link AuthContextDefaults}) is a
 * dev-only fallback for gateway-less local runs — never set it in production.
 */
public final class AuthContextHolder {

    private static volatile String defaultTenantId = "";

    private AuthContextHolder() {
    }

    static void setDefaultTenantId(final String tenantId) {
        defaultTenantId = tenantId == null ? "" : tenantId;
    }

    public static Optional<AuthContext> get() {
        final Optional<io.labs64.authcontext.UserContext> userContext = UserContextHolder.get();
        if (userContext.isPresent()) {
            final String tenantId = userContext.get().tenantId() != null
                    ? userContext.get().tenantId()
                    : fallbackTenantId();
            if (tenantId == null) {
                return Optional.empty();
            }
            return Optional.of(new AuthContext(tenantId, userContext.get().roles()));
        }
        if (StringUtils.isNotBlank(defaultTenantId)) {
            return Optional.of(new AuthContext(defaultTenantId, Set.of()));
        }
        return Optional.empty();
    }

    public static AuthContext require() {
        return get().orElseThrow(TenantRequiredException::new);
    }

    public static String requireTenantId() {
        return require().tenantId();
    }

    private static String fallbackTenantId() {
        return StringUtils.isNotBlank(defaultTenantId) ? defaultTenantId : null;
    }
}
