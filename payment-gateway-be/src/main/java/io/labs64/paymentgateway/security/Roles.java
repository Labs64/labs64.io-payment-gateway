package io.labs64.paymentgateway.security;

/**
 * Operation-level role names (authorization split: path-level RBAC is
 * enforced at the gateway via the module's role-mapping fragment; finer
 * operation checks live here, against the trusted {@code X-Auth-Roles}).
 */
public final class Roles {

    /** Required for payment-provider configuration access (PSP credentials). */
    public static final String PAYMENT_PROVIDER_ADMIN = "admin-role";

    private Roles() {
    }
}
