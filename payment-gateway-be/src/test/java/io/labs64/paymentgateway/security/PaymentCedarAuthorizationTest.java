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
import org.springframework.core.io.ClassPathResource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerMapping;

import io.labs64.authcontext.cedar.AuthorizationDecision;
import io.labs64.authcontext.cedar.AuthorizeInterceptor;
import io.labs64.authcontext.cedar.CedarAuthorizationService;
import io.labs64.authcontext.cedar.CedarProperties;
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
 * RFC-05 P3/P4: the REAL domain policy set — now GENERATED from the OpenAPI
 * {@code x-labs64-auth} ({@code classpath:auth-policy-domain.cedar}) — plus the
 * real resolver and the {@code @Authorize} PEP, in both modes. Cedar enforces
 * scope + cross-tenant isolation; workflow-state rules (payable-only-when-READY)
 * are the SERVICE layer's job now, not Cedar's.
 */
@ExtendWith(MockitoExtension.class)
class PaymentCedarAuthorizationTest {

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

    private AuthorizeInterceptor interceptor(final CedarProperties.Mode mode) {
        CedarProperties properties = new CedarProperties();
        properties.setEnabled(true);
        properties.setMode(mode);
        CedarAuthorizationService service = new CedarAuthorizationService(properties,
                new ClassPathResource("auth-policy-domain.cedar"));
        CedarDecisionAuditPublisher metrics = new CedarDecisionAuditPublisher(meterRegistry);
        return new AuthorizeInterceptor(service,
                List.of(new PaymentCedarEntityResolver(paymentService, messages)),
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
        assertThat(invoke(interceptor(CedarProperties.Mode.ENFORCE), "payPayment")).isTrue();
        assertThat(decisions.get(0).allowed()).isTrue();
    }

    @Test
    void cedarIsStatusAgnostic_workflowEnforcementIsServiceLayer() throws Exception {
        // Aligned model (OpenAPI single source of truth): the generated domain
        // policy checks scope + tenant only — NOT workflow status. A CLOSED
        // payment with the pay scope in-tenant is ALLOWED by Cedar; refusing to
        // pay a non-READY payment is the service layer's responsibility
        // (PaymentServiceImpl.ensurePayable), no longer Cedar's.
        authenticate("payment:pay");
        stubPayment(TENANT, PaymentStatus.CLOSED);
        assertThat(invoke(interceptor(CedarProperties.Mode.ENFORCE), "payPayment")).isTrue();
        assertThat(decisions.get(0).allowed()).isTrue();
    }

    @Test
    void enforceDeniesWithoutPayScope() throws Exception {
        authenticate("payment:read");
        stubPayment(TENANT, PaymentStatus.READY);
        assertThat(invoke(interceptor(CedarProperties.Mode.ENFORCE), "payPayment")).isFalse();
    }

    @Test
    void tenantGuardBlocksScopingBug() throws Exception {
        // Simulate a data-scoping bug: the lookup returns another tenant's
        // payment. The structural Cedar guard must still deny (F4 backstop).
        authenticate("payment:pay");
        stubPayment("t_999", PaymentStatus.READY);
        assertThat(invoke(interceptor(CedarProperties.Mode.ENFORCE), "payPayment")).isFalse();
        assertThat(response.getStatus()).isEqualTo(403);
    }

    @Test
    void shadowModeAuditsButNeverBlocks() throws Exception {
        authenticate("payment:read"); // pay would be denied
        stubPayment(TENANT, PaymentStatus.CLOSED);
        assertThat(invoke(interceptor(CedarProperties.Mode.SHADOW), "payPayment")).isTrue();
        assertThat(decisions.get(0).allowed()).isFalse();
        assertThat(decisions.get(0).enforced()).isFalse();
        assertThat(meterRegistry.get("labs64_authz_decisions_total")
                .tag("decision", "deny").tag("mode", "shadow").counter().count()).isEqualTo(1.0);
    }

    @Test
    void closeAllowsNonClosedWithWriteScope() throws Exception {
        authenticate("payment:write");
        stubPayment(TENANT, PaymentStatus.READY);
        assertThat(invoke(interceptor(CedarProperties.Mode.ENFORCE), "closePayment")).isTrue();
    }

    @Test
    void readRequiresReadScope() throws Exception {
        authenticate("payment:read");
        stubPayment(TENANT, PaymentStatus.READY);
        assertThat(invoke(interceptor(CedarProperties.Mode.ENFORCE), "getPayment")).isTrue();
    }

    @Test
    void unknownPaymentSurfacesAs404NotA403() {
        authenticate("payment:pay");
        when(paymentService.find(eq(TENANT), any(UUID.class))).thenReturn(Optional.empty());
        when(messages.notFound(any(UUID.class))).thenReturn("not found");
        assertThatThrownBy(() -> invoke(interceptor(CedarProperties.Mode.ENFORCE), "payPayment"))
                .isInstanceOf(NotFoundException.class);
    }
}
