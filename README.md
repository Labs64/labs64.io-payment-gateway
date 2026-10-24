# Labs64.IO :: Payment Gateway

**One API for every payment provider: charged once, never twice.**
*Part of the [Labs64.IO Ecosystem](https://labs64.io): the payments layer your shop, app or billing service calls.*

[![CI](https://github.com/Labs64/labs64.io-payment-gateway/actions/workflows/labs64io-be-ci.yml/badge.svg)](https://github.com/Labs64/labs64.io-payment-gateway/actions/workflows/labs64io-be-ci.yml)
[![Docker Image Version](https://img.shields.io/docker/v/labs64/payment-gateway?label=Docker&logo=docker&logoColor=white&color=1C90ED)](https://hub.docker.com/r/labs64/payment-gateway)
[![Artifact Hub](https://img.shields.io/endpoint?url=https://artifacthub.io/badge/repository/labs64io-helm-charts)](https://artifacthub.io/packages/helm/labs64io-helm-charts/payment-gateway)
[![License: Apache 2.0](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](LICENSE)
[![📖 Documentation](https://img.shields.io/badge/📖-Documentation-AB6543.svg)](https://labs64.io/docs/modules/payment-gateway/)

<p align="center"><img src="docs/assets/payment-gateway-hero.svg" alt="Payment Gateway: A shop retries POST /payments three times with the same Idempotency-Key; Payment Gateway replays the same answer and prints exactly one receipt: $30.00, routed to Stripe, confirmed, charged once." width="880"></p>

[Why Payment Gateway?](#why-payment-gateway) · [Documentation](https://labs64.io/docs/modules/payment-gateway/) · [Build and test](#build-and-test) · [Ecosystem](#the-labs64io-ecosystem) · [labs64.io](https://labs64.io)

> [!NOTE]
> Stripe, PayPal and a NoOp test provider ship in the box; add your own through the provider SPI. Every provider is configured **per tenant**, so one deployment can serve many merchants.

## Why Payment Gateway?

Adding a payment provider is easy. Living with three of them is not: different APIs, different webhook formats, different failure modes, and a double charge is always one network timeout away. Payment Gateway gives your applications one contract and keeps the provider-specific mess behind it.

1. **One REST API.** Create a payment, pay it, read the result: the same calls for Stripe, PayPal or a provider you add yourself.
2. **Safe to retry.** Pay with an `Idempotency-Key` and a replay returns the original transaction instead of charging again.
3. **One result shape.** Provider responses and webhooks are normalized into the same payment and transaction states, so your code does not care who processed the money.

**A good fit if**

- You take payments through more than one provider, or expect to switch or add one without touching every application.
- Several applications or merchants (tenants) need payments, and you want credentials and rules kept per tenant.
- You want payment lifecycle events in your audit trail without writing the glue.

**Probably not what you need if**

- You have one provider and one application and are happy with its SDK: the abstraction would be overhead.
- You need a hosted storefront, subscriptions billing UI or invoicing: this is the payment API layer, not a billing suite.

## Documentation

Install, configure and operate Payment Gateway in the **[Payment Gateway documentation](https://labs64.io/docs/modules/payment-gateway/)**.

[Quick Start](https://labs64.io/docs/modules/payment-gateway/#quick-start) · [Configuration](https://labs64.io/docs/modules/payment-gateway/#configuration) · [REST APIs](https://labs64.io/docs/modules/payment-gateway/#rest-apis) · [Before you take real payments](https://labs64.io/docs/modules/payment-gateway/#before-you-take-real-payments)

New to Labs64.IO? The [Quickstart](https://labs64.io/docs/getting-started/quickstart.html) puts the platform on any Kubernetes cluster in about five minutes.

## Build and test

```bash
cd payment-gateway-be
just test          # mvn clean verify
just unit-test     # unit tests only
just infra-up      # PostgreSQL, Redis and Cerbos
just run           # build and run with the local profile
```

Needs JDK 25, Maven 3.6.3+ and Docker. [`CONTRIBUTING.md`](CONTRIBUTING.md) covers pull requests. Working on the code with an AI agent? [`AGENTS.md`](AGENTS.md) and [`CLAUDE.md`](CLAUDE.md) describe the layout, conventions and checks.

## The Labs64.IO Ecosystem

This repository is part of the [Labs64.IO Ecosystem](https://labs64.io): open-source building blocks for secure commerce platforms. Every module is documented at [labs64.io/docs](https://labs64.io/docs/).

| Module | What it does |
|---|---|
| [**AuditFlow**](https://github.com/Labs64/labs64.io-auditflow) | Publish once, route anywhere, lose nothing |
| [**Auth Gateway**](https://github.com/Labs64/labs64.io-authproxy) | One authenticated edge for every module |
| [**Payment Gateway**](https://github.com/Labs64/labs64.io-payment-gateway) | One API across multiple payment providers |
| [**Checkout**](https://github.com/Labs64/labs64.io-checkout) | Whitelabel checkout and order workflow |
| [**Customer Portal**](https://github.com/Labs64/labs64.io-customer-portal) | Self-service portal for your customers |

Built on [Commons](https://github.com/Labs64/labs64.io-commons), deployed with [Helm Charts](https://github.com/Labs64/labs64.io-helm-charts), developed in the [Workspace](https://github.com/Labs64/labs64.io-workspace), verified by [Tests](https://github.com/Labs64/labs64.io-tests) and documented in [Docs](https://github.com/Labs64/labs64.io-docs). Also from Labs64: [NetLicensing](https://netlicensing.io) for licensing-as-a-service and [GuideChimp](https://www.labs64.com/guidechimp/) for guided product tours.

## Contributing

We would love your help. The most valuable contribution is a new payment provider: start from the SPI in [`payment-gateway-providers/providers-spi`](payment-gateway-providers/providers-spi/) and use the `noop` module as a template. Fork the repository, make your change on a branch, run the checks described in [`AGENTS.md`](AGENTS.md) and open a pull request. Not sure whether an idea fits? Open an issue first.

## License

Open source under the [Apache License 2.0](LICENSE): use it, change it and ship it commercially.

---

<p align="center">
  <a href="https://labs64.io"><img alt="Labs64.IO" src="https://labs64.io/docs/assets/images/labs64-logo.png" width="110"></a><br>
  <sub>Made by the <a href="https://labs64.io">Labs64</a> team and community.</sub>
</p>
