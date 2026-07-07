package io.labs64.paymentgateway.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import io.labs64.authcontext.UserContext;
import io.labs64.authcontext.UserContextHolder;
import io.labs64.paymentgateway.exception.TenantRequiredException;

class AuthContextHolderTest {

    @AfterEach
    void cleanup() {
        UserContextHolder.clear();
        AuthContextHolder.setDefaultTenantId("");
    }

    @Test
    void mapsBoundUserContextToAuthContext() {
        UserContextHolder.set(new UserContext("jdoe", "t_100", Set.of("admin-role"), "req-1"));

        final AuthContext context = AuthContextHolder.require();

        assertThat(context.tenantId()).isEqualTo("t_100");
        assertThat(context.hasRole("admin-role")).isTrue();
        assertThat(context.hasRole(Roles.PAYMENT_PROVIDER_ADMIN)).isTrue();
        assertThat(context.hasAnyRole("other-role", "admin-role")).isTrue();
    }

    @Test
    void tenantlessContextWithoutDefaultIsEmpty() {
        UserContextHolder.set(new UserContext("jdoe", null, Set.of("admin-role"), "req-1"));

        assertThat(AuthContextHolder.get()).isEmpty();
        assertThatThrownBy(AuthContextHolder::require).isInstanceOf(TenantRequiredException.class);
    }

    @Test
    void tenantlessContextFallsBackToDefaultTenant() {
        AuthContextHolder.setDefaultTenantId("t_dev");
        UserContextHolder.set(new UserContext("jdoe", null, Set.of("admin-role"), "req-1"));

        assertThat(AuthContextHolder.requireTenantId()).isEqualTo("t_dev");
    }

    @Test
    void noContextFallsBackToDefaultTenantWithNoRoles() {
        AuthContextHolder.setDefaultTenantId("t_dev");

        final AuthContext context = AuthContextHolder.require();

        assertThat(context.tenantId()).isEqualTo("t_dev");
        assertThat(context.roles()).isEmpty();
    }

    @Test
    void noContextAndNoDefaultIsEmpty() {
        assertThat(AuthContextHolder.get()).isEmpty();
        assertThatThrownBy(AuthContextHolder::requireTenantId).isInstanceOf(TenantRequiredException.class);
    }
}
