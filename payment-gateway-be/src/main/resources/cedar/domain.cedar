// payment-gateway domain Cedar policy set (RFC-05 P3 pilot, Tier 2).
//
// Validated in CI against the ONE shared schema
// (labs64.io-commons/auth-policy-cedar/schema.cedarschema) — same type system
// as every other module and the edge tier, so drift is a build failure (F3).
// Evaluated in-process by the @Authorize PEP (auth-context-spring-boot-starter).

// ── Tenant isolation guard (F4): no principal may act on another tenant's
// payment; forbid overrides any permit below. Proven load-bearing by the
// commons cross-tenant isolation gate.
forbid(principal, action, resource is Labs64IO::Payment)
unless { principal in resource.tenant };

// ── Read requires the read scope ──
permit(
  principal,
  action == Labs64IO::Action::"readPayment",
  resource
) when { context.scopes.contains("payment:read") };

// ── Pay: scope AND workflow state (F6 — boolean conditions, not OR-scope).
// Mirrors PaymentServiceImpl.ensurePayable (only READY payments are payable).
permit(
  principal,
  action == Labs64IO::Action::"payPayment",
  resource
) when { resource.status == "READY" && context.scopes.contains("payment:pay") };

// ── Close: any non-closed payment, write scope ──
permit(
  principal,
  action == Labs64IO::Action::"closePayment",
  resource
) when { resource.status != "CLOSED" && context.scopes.contains("payment:write") };
