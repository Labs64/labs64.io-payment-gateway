package io.labs64.paymentgateway.security;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Temporary development authentication adapter.
 * <p>
 * Reads tenant and scopes from request headers until JWT token support is
 * implemented. Remove this filter when the payment gateway starts validating
 * real JWT access tokens.
 */
@Component
@ConditionalOnProperty(prefix = "labs64.security.dev-auth", name = "enabled", havingValue = "true",
        matchIfMissing = true)
public class DevAuthFilter extends OncePerRequestFilter {

    public static final String TENANT_HEADER = "X-Tenant-ID";
    public static final String SCOPES_HEADER = "X-Scopes";
    private static final String SCOPE_PREFIX = "SCOPE_";
    private static final String MDC_TENANT_ID = "tenantId";

    @Override
    protected void doFilterInternal(final HttpServletRequest request, @NotNull final HttpServletResponse response,
                                    @NotNull final FilterChain filterChain) throws ServletException, IOException {
        final String tenantId = request.getHeader(TENANT_HEADER);

        if (StringUtils.isNotBlank(tenantId)) {
            final AuthPrincipal principal = new AuthPrincipal(tenantId);
            final Collection<SimpleGrantedAuthority> authorities = parseScopes(request.getHeader(SCOPES_HEADER));
            final UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(principal, null, authorities);

            SecurityContextHolder.getContext().setAuthentication(authentication);
            MDC.put(MDC_TENANT_ID, tenantId);
        }

        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_TENANT_ID);
            SecurityContextHolder.clearContext();
        }
    }

    private Collection<SimpleGrantedAuthority> parseScopes(final String scopesHeader) {
        if (StringUtils.isBlank(scopesHeader)) {
            return java.util.List.of();
        }

        return Arrays.stream(scopesHeader.split("[,\\s]+"))
                .map(String::trim)
                .filter(StringUtils::isNotBlank)
                .map(scope -> scope.startsWith(SCOPE_PREFIX) ? scope : SCOPE_PREFIX + scope)
                .map(SimpleGrantedAuthority::new)
                .toList();
    }
}
