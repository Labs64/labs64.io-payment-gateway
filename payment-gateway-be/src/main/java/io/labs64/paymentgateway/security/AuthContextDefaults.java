package io.labs64.paymentgateway.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

/** Wires the dev-only {@code labs64.tenant.default} into {@link AuthContextHolder}. */
@Component
public class AuthContextDefaults {

    private final String defaultTenantId;

    public AuthContextDefaults(@Value("${labs64.tenant.default:}") final String defaultTenantId) {
        this.defaultTenantId = defaultTenantId;
    }

    @PostConstruct
    void init() {
        AuthContextHolder.setDefaultTenantId(defaultTenantId);
    }
}
