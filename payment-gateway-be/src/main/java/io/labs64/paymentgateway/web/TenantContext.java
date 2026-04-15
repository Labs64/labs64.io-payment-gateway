package io.labs64.paymentgateway.web;

/**
 * Thread-local holder for the current tenant ID extracted from JWT.
 * Must be set/cleared per request (typically via a filter or interceptor).
 */
public final class TenantContext {

    private static final ThreadLocal<String> CURRENT_TENANT = new ThreadLocal<>();

    private TenantContext() {
    }

    public static String getTenantId() {
        return CURRENT_TENANT.get();
    }

    public static void setTenantId(final String tenantId) {
        CURRENT_TENANT.set(tenantId);
    }

    public static void clear() {
        CURRENT_TENANT.remove();
    }
}
