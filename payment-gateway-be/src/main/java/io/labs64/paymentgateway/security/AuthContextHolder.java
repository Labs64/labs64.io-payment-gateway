package io.labs64.paymentgateway.security;

import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import io.labs64.paymentgateway.exception.TenantRequiredException;

/**
 * Reads the current application auth context from Spring Security.
 */
public class AuthContextHolder {

    private static final String SCOPE_PREFIX = "SCOPE_";

    public AuthContextHolder() {
    }

    public static Optional<AuthContext> get() {
        final Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return Optional.empty();
        }

        final String tenantId = tenantId(authentication);
        if (tenantId == null || tenantId.isBlank()) {
            return Optional.empty();
        }

        final Set<String> scopes = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(authority -> authority != null && authority.startsWith(SCOPE_PREFIX))
                .map(authority -> authority.substring(SCOPE_PREFIX.length()))
                .collect(Collectors.toUnmodifiableSet());

        return Optional.of(new AuthContext(tenantId, scopes));
    }

    public static AuthContext require() {
        return get().orElseThrow(TenantRequiredException::new);
    }

    public static String requireTenantId() {
        return require().tenantId();
    }

    private static String tenantId(final Authentication authentication) {
        if (authentication.getPrincipal() instanceof AuthPrincipal(String tenantId)) {
            return tenantId;
        }
        return null;
    }
}
