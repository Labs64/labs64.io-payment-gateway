package io.labs64.paymentgateway.security;

import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

import io.labs64.authcontext.authorization.ResourceEntity;
import io.labs64.authcontext.authorization.ResourceResolver;
import io.labs64.authcontext.core.AuthContext;
import io.labs64.paymentgateway.entity.PaymentEntity;
import io.labs64.paymentgateway.exception.NotFoundException;
import io.labs64.paymentgateway.message.PaymentMessages;
import io.labs64.paymentgateway.service.PaymentService;

/**
 * Supplies the {@code Payment} resource for {@code @Authorize} checks. The
 * lookup is tenant-scoped, so a cross-tenant id surfaces as the module's
 * regular 404 — never a 403 that would leak existence; the PDP tenant guard
 * stays the structural backstop for any path that bypasses scoping.
 */
@Component
public class PaymentResourceResolver implements ResourceResolver {

    private final PaymentService paymentService;
    private final PaymentMessages messages;

    public PaymentResourceResolver(final PaymentService paymentService, final PaymentMessages messages) {
        this.paymentService = paymentService;
        this.messages = messages;
    }

    @Override
    public boolean supports(final String resourceType) {
        return "Payment".equals(resourceType);
    }

    @Override
    public ResourceEntity resolve(final String resourceType, @Nullable final Object resourceRef,
            final AuthContext context) {
        final UUID paymentId = UUID.fromString(String.valueOf(resourceRef));
        final PaymentEntity payment = paymentService.find(context.tenantId(), paymentId)
                .orElseThrow(() -> new NotFoundException(messages.notFound(paymentId)));
        // Tenant-only: the domain policy is the generated OpenAPI contract
        // (scope/tenant + the cross-tenant guard). Workflow-state rules (e.g.
        // payable-only-when-READY) stay in the service layer, not in authz.
        return ResourceEntity.builder("Payment", payment.getId().toString())
                .attribute("tenant", payment.getTenantId())
                .build();
    }
}
