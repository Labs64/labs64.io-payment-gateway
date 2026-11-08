# PayPal payment provider

Setup, configuration and troubleshooting of this provider are in the [PayPal guide](https://labs64.io/docs/modules/payment-gateway/paypal/).

## Automated PSP integration coverage

The opt-in Robot suite `tests/e2e/paypal_psp_flow.robot` runs the built Payment Gateway through
the public gateway edge while the real PayPal Java SDK talks to external WireMock. It covers the
OAuth, create-order, capture-order, and webhook-verification HTTP contracts; idempotent replay;
upstream and incomplete responses; browser return/cancel; approved-order capture fallback;
completed and denied events; verification rejection; and terminal-state protection.

```bash
cd ../labs64.io-tests
just enable-psp-stub
just test-psp
just disable-psp-stub
```

The deterministic suite verifies our integration and state transitions. It does not prove that
PayPal credentials, account configuration, hosted checkout, or the live PayPal network are healthy.
