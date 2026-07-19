package io.labs64.paymentgateway.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerMapping;

import io.labs64.authcontext.authorization.AuthorizationDecision;
import io.labs64.authcontext.authorization.AuthorizationProperties;
import io.labs64.authcontext.authorization.AuthorizationService;
import io.labs64.authcontext.authorization.AuthorizeInterceptor;
import io.labs64.authcontext.authorization.ResourceEntity;
import io.labs64.authcontext.core.AuthContext;
import io.labs64.authcontext.core.AuthContextHolder;
import io.labs64.paymentgateway.controller.PaymentController;
import io.labs64.paymentgateway.entity.PaymentEntity;
import io.labs64.paymentgateway.exception.NotFoundException;
import io.labs64.paymentgateway.message.PaymentMessages;
import io.labs64.paymentgateway.model.PayPaymentRequest;
import io.labs64.paymentgateway.model.PaymentStatus;
import io.labs64.paymentgateway.service.PaymentService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

/**
 * rename migration: the real {@link PaymentResourceResolver} + the
 * {@code @Authorize} PEP, exercised in both modes against a stub
 * {@link AuthorizationService} that mirrors the generated Cerbos domain policy
 * (scope-per-action + cross-tenant guard, status-agnostic). The real Cerbos
 * client lives in the commons {@code CerbosAuthorizationServiceTest}; whole-
 * policy decision equivalence is proven by the commons {@code auth-policy-cerbos}
 * truth-table gate. Workflow-state rules (payable-only-when-READY) are the
 * SERVICE layer's job, never authz's.
 */
@ExtendWith(MockitoExtension.class)
class PaymentAuthorizationTest {

    private static final String TENANT = "t_100";
    private static final UUID PAYMENT_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440042");

    @Mock
    private PaymentService paymentService;

    @Mock
    private PaymentMessages messages;

    private final List<AuthorizationDecision> decisions = new ArrayList<>();
    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    private MockHttpServletResponse response;

    @BeforeEach
    void resetResponse() {
        response = new MockHttpServletResponse();
    }

    @AfterEach
    void cleanup() {
        AuthContextHolder.clear();
    }

    /**
     * Stub PDP mirroring the generated payment-gateway domain policy: each
     * action needs its scope, and the tenant guard denies whenever the resource
     * carries a tenant that differs from (or is absent on) the principal. It is
     * deliberately status-agnostic — workflow state is the service layer's job.
     */
    private static final class StubAuthorizationService implements AuthorizationService {

        private static final Map<String, String> REQUIRED_SCOPE = Map.of(
                "payPayment", "payment:pay",
                "getPayment", "payment:read",
                "closePayment", "payment:write");

        private final AuthorizationProperties.Mode mode;

        StubAuthorizationService(final AuthorizationProperties.Mode mode) {
            this.mode = mode;
        }

        @Override
        public boolean isEnforcing() {
            return mode == AuthorizationProperties.Mode.ENFORCE;
        }

        @Override
        public AuthorizationDecision decide(final AuthContext ctx, final String action, final ResourceEntity resource) {
            Object tenant = resource.attributes().get("tenant");
            boolean tenantGuard = tenant == null || tenant.equals(ctx.tenantId());
            String required = REQUIRED_SCOPE.get(action);
            boolean scopeOk = required == null || ctx.hasScope(required);
            boolean allowed = tenantGuard && scopeOk;
            return new AuthorizationDecision(action, resource.type(), resource.id(),
                    allowed, isEnforcing(), allowed ? List.of("policy0") : List.of(), null,
                    ctx.userId(), ctx.tenantId(), ctx.requestId());
        }
    }

    private AuthorizeInterceptor interceptor(final AuthorizationProperties.Mode mode) {
        AuthorizationDecisionAuditPublisher metrics = new AuthorizationDecisionAuditPublisher(meterRegistry);
        return new AuthorizeInterceptor(new StubAuthorizationService(mode),
                List.of(new PaymentResourceResolver(paymentService, messages)),
                List.of(decisions::add, metrics));
    }

    private void stubPayment(final String tenantId, final PaymentStatus status) {
        PaymentEntity payment = PaymentEntity.builder()
                .id(PAYMENT_ID).tenantId(tenantId).status(status).build();
        when(paymentService.find(eq(TENANT), eq(PAYMENT_ID))).thenReturn(Optional.of(payment));
    }

    private boolean invoke(final AuthorizeInterceptor interceptor, final String methodName) throws Exception {
        Method method = methodName.equals("payPayment")
                ? PaymentController.class.getMethod(methodName, UUID.class, PayPaymentRequest.class)
                : PaymentController.class.getMethod(methodName, UUID.class);
        HandlerMethod handler = new HandlerMethod(mock(PaymentController.class), method);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE,
                Map.of("paymentId", PAYMENT_ID.toString()));
        return interceptor.preHandle(request, response, handler);
    }

    private void authenticate(final String... scopes) {
        AuthContextHolder.set(new AuthContext("alice", TENANT, Set.of(scopes), "r-1"));
    }

    @Test
    void enforceAllowsPayingReadyPaymentWithScope() throws Exception {
        authenticate("payment:pay");
        stubPayment(TENANT, PaymentStatus.READY);
        assertThat(invoke(interceptor(AuthorizationProperties.Mode.ENFORCE), "payPayment")).isTrue();
        assertThat(decisions.get(0).allowed()).isTrue();
    }

    @Test
    void authzIsStatusAgnostic_workflowEnforcementIsServiceLayer() throws Exception {
        // Aligned model (OpenAPI single source of truth): the generated domain
        // policy checks scope + tenant only — NOT workflow status. A CLOSED
        // payment with the pay scope in-tenant is ALLOWED by authz; refusing to
        // pay a non-READY payment is the service layer's responsibility
        // (PaymentServiceImpl.ensurePayable), no longer the policy's.
        authenticate("payment:pay");
        stubPayment(TENANT, PaymentStatus.CLOSED);
        assertThat(invoke(interceptor(AuthorizationProperties.Mode.ENFORCE), "payPayment")).isTrue();
        assertThat(decisions.get(0).allowed()).isTrue();
    }

    @Test
    void enforceDeniesWithoutPayScope() throws Exception {
        authenticate("payment:read");
        stubPayment(TENANT, PaymentStatus.READY);
        assertThat(invoke(interceptor(AuthorizationProperties.Mode.ENFORCE), "payPayment")).isFalse();
    }

    @Test
    void tenantGuardBlocksScopingBug() throws Exception {
        // Simulate a data-scoping bug: the lookup returns another tenant's
        // payment. The structural tenant guard must still deny (F4 backstop).
        authenticate("payment:pay");
        stubPayment("t_999", PaymentStatus.READY);
        assertThat(invoke(interceptor(AuthorizationProperties.Mode.ENFORCE), "payPayment")).isFalse();
        assertThat(response.getStatus()).isEqualTo(403);
    }

    @Test
    void shadowModeAuditsButNeverBlocks() throws Exception {
        authenticate("payment:read"); // pay would be denied
        stubPayment(TENANT, PaymentStatus.CLOSED);
        assertThat(invoke(interceptor(AuthorizationProperties.Mode.SHADOW), "payPayment")).isTrue();
        assertThat(decisions.get(0).allowed()).isFalse();
        assertThat(decisions.get(0).enforced()).isFalse();
        assertThat(meterRegistry.get("labs64_authz_decisions_total")
                .tag("decision", "deny").tag("mode", "shadow").counter().count()).isEqualTo(1.0);
    }

    @Test
    void closeAllowsNonClosedWithWriteScope() throws Exception {
        authenticate("payment:write");
        stubPayment(TENANT, PaymentStatus.READY);
        assertThat(invoke(interceptor(AuthorizationProperties.Mode.ENFORCE), "closePayment")).isTrue();
    }

    @Test
    void readRequiresReadScope() throws Exception {
        authenticate("payment:read");
        stubPayment(TENANT, PaymentStatus.READY);
        assertThat(invoke(interceptor(AuthorizationProperties.Mode.ENFORCE), "getPayment")).isTrue();
    }

    @Test
    void unknownPaymentSurfacesAs404NotA403() {
        authenticate("payment:pay");
        when(paymentService.find(eq(TENANT), any(UUID.class))).thenReturn(Optional.empty());
        when(messages.notFound(any(UUID.class))).thenReturn("not found");
        assertThatThrownBy(() -> invoke(interceptor(AuthorizationProperties.Mode.ENFORCE), "payPayment"))
                .isInstanceOf(NotFoundException.class);
    }
}
