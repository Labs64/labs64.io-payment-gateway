package io.labs64.paymentgateway.web;

import java.io.IOException;

import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;

/**
 * Servlet filter that extracts {@code tenantId} from the JWT token
 * and stores it in {@link TenantContext} for the duration of the request.
 * <p>
 * For Milestone 2 (no real JWT verification), the tenant ID is taken from
 * a request header {@code X-Tenant-ID} for testing purposes.
 * In production, this will be replaced with JWT claim extraction.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class TenantFilter implements Filter {

    public static final String TENANT_HEADER = "X-Tenant-ID";
    private static final String MDC_TENANT_ID = "tenantId";

    @Override
    public void doFilter(final ServletRequest request, final ServletResponse response, final FilterChain chain)
            throws IOException, ServletException {
        final HttpServletRequest httpRequest = (HttpServletRequest) request;

        // TODO: Replace with JWT claim extraction (tenantId claim) when JWT security is implemented
        final String tenantId = httpRequest.getHeader(TENANT_HEADER);

        if (tenantId != null && !tenantId.isBlank()) {
            TenantContext.setTenantId(tenantId);
            MDC.put(MDC_TENANT_ID, tenantId);
        }

        try {
            chain.doFilter(request, response);
        } finally {
            TenantContext.clear();
            MDC.remove(MDC_TENANT_ID);
        }
    }
}
