# Stripe payment provider

Setup, configuration and troubleshooting of this provider are in the [Stripe guide](https://labs64.io/docs/modules/payment-gateway/stripe/).

## Automated PSP integration coverage

The opt-in Robot suite `tests/e2e/stripe_psp_flow.robot` runs the built Payment Gateway through
the public gateway edge while the real Stripe Java SDK talks to an external WireMock process.
It covers:

- the outbound create-session HTTP contract, including amount, currency, metadata, callbacks,
  authorization, and the provider-side idempotency key;
- replay of the same `/pay` request without a second Stripe call;
- Stripe 5xx and incomplete-success responses recorded as non-terminal `PENDING`
  transactions with normalized `PROVIDER_*` status details;
- paid and unpaid browser returns, cancellation, tenant redirects, and persisted state;
- valid success/failure webhook signatures, invalid-signature rejection, and protection of a
  terminal successful transaction from a late failure event.

Run it through the shared test orchestrator:

```bash
cd ../labs64.io-tests
just enable-psp-stub
just test-psp
just disable-psp-stub
```

This deterministic suite verifies our integration contract and state transitions. It does not
prove that Stripe's hosted UI, credentials, account configuration, or live network are healthy;
those remain the responsibility of a small separately scheduled smoke flow against Stripe test
mode.
