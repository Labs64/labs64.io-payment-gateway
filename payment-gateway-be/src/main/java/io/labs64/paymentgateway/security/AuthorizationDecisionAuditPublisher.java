package io.labs64.paymentgateway.security;

import org.springframework.stereotype.Component;

import io.labs64.authcontext.authorization.AuthorizationDecision;
import io.labs64.authcontext.authorization.AuthorizationDecisionListener;
import io.labs64.authcontext.authorization.LoggingDecisionListener;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

/**
 * Publishes every authorization decision: structured log line
 * (via the commons logging listener format) + Prometheus counter
 * {@code labs64_authz_decisions_total{action,decision,mode}}.
 *
 * <p>TODO( P4 follow-up): route decisions to AuditFlow through the
 * payment event stream once the module's business audit events are wired
 * (tracked with the existing unwired-audit-events finding).
 */
@Component
public class AuthorizationDecisionAuditPublisher implements AuthorizationDecisionListener {

    private final LoggingDecisionListener log = new LoggingDecisionListener();
    private final MeterRegistry meterRegistry;

    public AuthorizationDecisionAuditPublisher(final MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    @Override
    public void onDecision(final AuthorizationDecision decision) {
        log.onDecision(decision);
        Counter.builder("labs64_authz_decisions_total")
                .description("authorization decisions")
                .tag("action", decision.action())
                .tag("decision", decision.allowed() ? "allow" : "deny")
                .tag("mode", decision.enforced() ? "enforce" : "shadow")
                .register(meterRegistry)
                .increment();
    }
}
