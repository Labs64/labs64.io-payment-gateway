# Labs64.IO :: Payment Gateway

**One API for every payment provider: charged once, never twice.**
*Part of the [Labs64.IO Ecosystem](https://labs64.io): the payments layer your shop, app or billing service calls.*

[![CI](https://github.com/Labs64/labs64.io-payment-gateway/actions/workflows/labs64io-be-ci.yml/badge.svg)](https://github.com/Labs64/labs64.io-payment-gateway/actions/workflows/labs64io-be-ci.yml)
[![Docker Image Version](https://img.shields.io/docker/v/labs64/payment-gateway?label=Docker&logo=docker&logoColor=white&color=1C90ED)](https://hub.docker.com/r/labs64/payment-gateway)
[![Artifact Hub](https://img.shields.io/endpoint?url=https://artifacthub.io/badge/repository/labs64io-helm-charts)](https://artifacthub.io/packages/helm/labs64io-helm-charts/payment-gateway)
[![License: Apache 2.0](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](LICENSE)
[![📖 Documentation](https://img.shields.io/badge/📖-Documentation-AB6543.svg)](https://labs64.io/docs/modules/payment-gateway/)

<p align="center"><img src="docs/assets/payment-gateway-hero.svg" alt="Payment Gateway: shops, apps and billing services call one payment API; it creates the payment, pays it idempotently and routes it to Stripe, PayPal, a test provider or your own." width="880"></p>

[**Get started**](#get-started) · [Features](#features) · [Architecture](#architecture) · [AI agents](#using-payment-gateway-with-ai-agents) · [Ecosystem](#the-labs64io-ecosystem) · [Documentation](https://labs64.io/docs/modules/payment-gateway/) · [labs64.io](https://labs64.io)

> [!NOTE]
> Stripe, PayPal and a NoOp test provider ship in the box; add your own through the provider SPI. Every provider is configured **per tenant**, so one deployment can serve many merchants.

## Why Payment Gateway?

Integrating each payment provider separately means separate APIs, separate webhook formats and separate retry bugs, and a double charge is one network timeout away. Payment Gateway puts one contract in front of all of them:

1. **One REST API.** Create a payment, pay it, read the result; the same calls for Stripe, PayPal or your own provider.
2. **Safe to retry.** Pay with an `Idempotency-Key`: a replay returns the original transaction instead of charging again.
3. **One result shape.** Provider responses and webhooks are normalized to the same payment and transaction states.

## Get started

Four ways in. Pick the one that matches what you have; each is complete on its own.

```
 1  install.sh             2  Kubernetes             3  Docker                 4  Source
┌───────────────────────┐ ┌───────────────────────┐ ┌───────────────────────┐ ┌───────────────────────┐
│ The whole ecosystem   │ │ Payment Gateway on    │ │ Payment Gateway on    │ │ Build it, change      │
│ with gateway + auth   │ │ your cluster and      │ │ your machine, from    │ │ it, add a provider    │
│ and a live smoke      │ │ your own PostgreSQL   │ │ the published image   │ │                       │
│ test                  │ │ and Redis             │ │                       │ │                       │
├───────────────────────┤ ├───────────────────────┤ ├───────────────────────┤ ├───────────────────────┤
│ needs  a cluster,     │ │ needs  a cluster,     │ │ needs  Docker         │ │ needs  JDK 25, Maven, │
│        kubectl, helm  │ │        kubectl, helm  │ │                       │ │        Docker         │
│ time   ~5 minutes     │ │ time   an afternoon   │ │ time   ~2 minutes     │ │ time   first build    │
└───────────────────────┘ └───────────────────────┘ └───────────────────────┘ └───────────────────────┘
 evaluate everything       run it for real           see a payment happen      work on the code
```

Not sure? Start with **3** to watch a payment go through in two minutes, or **1** to see it as part of the platform.

### 1. `install.sh`: the quick ecosystem start

One script installs Payment Gateway, AuditFlow and the authenticating API gateway on any Kubernetes cluster, then proves it with a real request. No cluster? A throwaway one on your laptop is enough:

```bash
k3d cluster create labs64io-demo -p "8000:80@loadbalancer" --k3s-arg "--disable=traefik@server:0"   # optional

curl -fsSL https://raw.githubusercontent.com/Labs64/labs64.io-helm-charts/master/install.sh -o install.sh
bash install.sh
```

Press Enter three times to take the defaults. You finish with a gateway you can `curl`, interactive API docs and a working token flow:

```text
  PASS  Obtained a demo access token from mock-oidc
  PASS  Published a demo audit event (HTTP 200) — first good request succeeded

 Labs64.IO is ready.
   Base URL:  http://localhost:8000
   API docs:  http://localhost:8000/swagger-ui/
```

Payment Gateway is then at `http://localhost:8000/payment-gateway/api/v1/`. The same script later runs `status`, `stop`, `start` and `uninstall`. This is an evaluation install with a demo identity provider: keep it on a local or private cluster. Details: [Quickstart](https://labs64.io/docs/getting-started/quickstart/) · [`install.sh`](https://github.com/Labs64/labs64.io-helm-charts/blob/master/install.sh).

### 2. Kubernetes: on your own cluster

Install Payment Gateway from its Helm chart and point it at your infrastructure:

```bash
helm repo add labs64io https://labs64.github.io/labs64.io-helm-charts
helm repo update
helm install my-payments labs64io/payment-gateway -n labs64io --create-namespace
```

Payment Gateway needs PostgreSQL and a Redis-compatible store (used for idempotency). The [`preflight`](https://labs64.io/docs/getting-started/deploy-to-kubernetes/) chart checks both from inside your cluster before you install. Guides: [Deploy to your Kubernetes cluster](https://labs64.io/docs/getting-started/deploy-to-kubernetes/) · [Helm chart on Artifact Hub](https://artifacthub.io/packages/helm/labs64io-helm-charts/payment-gateway).

### 3. Docker: on your machine

Only Docker and Git are needed. This uses the published image with PostgreSQL and Valkey next to it, so there is nothing to build.

**Start the stack** (the first run pulls the images):

```bash
git clone https://github.com/Labs64/labs64.io-payment-gateway.git && cd labs64.io-payment-gateway
docker compose -f docker-compose.images.yml up -d
```

**Pay for an order.** The built-in NoOp provider succeeds without a PSP account. Paste this block once the stack is healthy (about 30 seconds):

```bash
# Calls arrive with the identity headers the API gateway adds after authenticating a caller.
api() { curl -s -H "X-Auth-User: demo-user" -H "X-Auth-Tenant: demo" -H "X-Request-ID: demo" \
  -H "X-Auth-Scopes: payment-provider:read,payment-provider:write,payment:read,payment:write,payment:pay,payment-transaction:read" \
  -H "Content-Type: application/json" "$@"; }
id() { grep -o '"id":"[^"]*"' | head -1 | cut -d'"' -f4; }

# 1. Enable the NoOp provider for this tenant
PROVIDER=$(api -X POST localhost:8080/payment-providers -d '{"provider":"noop","active":true}' | id)

# 2. Create a payment for a 30.00 USD order
PAYMENT=$(api -X POST localhost:8080/payments -d '{
  "paymentProviderId": "'$PROVIDER'",
  "purchaseOrder": {"currency":"USD","netAmount":3000,"taxAmount":0,"grossAmount":3000,
                    "items":[{"name":"Widget Pro","sku":"WIDGET-PRO","price":3000,"quantity":1}]},
  "billingInfo": {"firstName":"Jane","lastName":"Doe","email":"jane.doe@example.com",
                  "country":"US","city":"San Francisco","address1":"123 Market St","postalCode":"94105"}
}' | id)

# 3. Pay it twice with the same Idempotency-Key
for i in 1 2; do
  api -X POST localhost:8080/payments/$PAYMENT/pay -H "Idempotency-Key: order-1001" \
    | grep -o '"status":"[A-Z]*"' | tr '\n' ' '; echo
done

# 4. How many transactions were created?
api "localhost:8080/payment-transactions?paymentId=$PAYMENT" | grep -o '"totalItems":[0-9]*'
```

```
"status":"CLOSED" "status":"SUCCESS"
"status":"CLOSED" "status":"SUCCESS"
"totalItems":1
```

You paid **twice** and were charged **once**: the replay returned the original transaction. The payment is `CLOSED`, the transaction `SUCCESS`.

**Clean up** with `docker compose -f docker-compose.images.yml down -v`. Prefer a UI? Open <http://localhost:8080/swagger-ui/index.html>.

This is an evaluation setup: it runs without an API gateway and with authorization off, so do not expose port 8080.

### 4. Source: build and change it

Needs Docker with Compose v2, JDK 25 and Maven 3.6.3+:

```bash
mvn -B clean package -DskipTests -pl payment-gateway-be -am   # build the backend and its modules
cd payment-gateway-be && cp .env.example .env
docker compose -f docker-compose.yml -f docker-compose.app.yml up -d --build
```

[`AGENTS.md`](AGENTS.md) describes the layout, commands and tests; [`CONTRIBUTING.md`](CONTRIBUTING.md) covers pull requests; the [Contributing guide](https://labs64.io/docs/development/contributing/) covers the workflow.

### Where next?

Everything below is in the [Payment Gateway documentation](https://labs64.io/docs/modules/payment-gateway/):

| I want to… | Read |
|---|---|
| Take real payments with Stripe or PayPal | [Configuring providers](https://labs64.io/docs/modules/payment-gateway/#configuration) and the [Stripe and PayPal demo runbook](examples/payment-gateway-stripe-paypal-demo.md) |
| Handle provider webhooks and returns | [REST APIs](https://labs64.io/docs/modules/payment-gateway/#rest-apis) |
| Follow payments in my audit log | [Events](https://labs64.io/docs/modules/payment-gateway/#events) |
| Compare all the ways to run Labs64.IO | [Choose a deployment path](https://labs64.io/docs/getting-started/choose-a-deployment-path/) |

## Features

- **Provider abstraction**: one `PaymentProvider` SPI; Stripe, PayPal and NoOp ship as modules, and adding a PSP is a new module, not a fork.
- **Idempotent payments**: Redis-backed `Idempotency-Key` handling; a replay returns the first result.
- **Webhooks and returns**: PSP webhooks and browser returns are handled per provider; terminal transactions are never overwritten by a late event.
- **Multi-tenant**: tenant, scopes and provider credentials come from the trusted gateway headers and per-tenant provider config.
- **Payment lifecycle events**: `payment.created`, `payment.finalized` and `payment.closed` can be published to AuditFlow over its HTTP API.
- **Recurring payments**: a payment can carry a recurrence rule (duration or cron).
- **OpenAPI-first**: the contract is the source of truth; models and server interfaces are generated from it.
- **Observable**: Actuator health, Micrometer tracing over OTLP and a Prometheus endpoint.

The complete list is in the [documentation](https://labs64.io/docs/modules/payment-gateway/).

## Architecture

```
  ┌──────────────────────────────────────────────┐
  │ Shop · app · billing service (any stack)     │
  └──────────────────────┬───────────────────────┘
                         │  POST /payments · POST /payments/{id}/pay
                         │  Idempotency-Key, tenant + scopes from the gateway
  ┌──────────────────────▼───────────────────────────────────────────────┐
  │ payment-gateway-be            Java 25 · Spring Boot 4                │
  │ tenants · idempotency · provider registry · webhooks                 │
  └───────┬──────────────────────┬──────────────────────┬────────────────┘
          │                      │                      │
  ┌───────▼────────┐    ┌────────▼───────┐    ┌─────────▼──────────────┐
  │ PostgreSQL     │    │ Valkey         │    │ AuditFlow (HTTP)       │
  │ payments,      │    │ idempotency    │    │ payment.created ·      │
  │ transactions   │    │ keys           │    │ finalized · closed     │
  └────────────────┘    └────────────────┘    └────────────────────────┘
          │ PaymentProvider SPI                          ▲ webhooks, returns
  ┌───────▼───────┐ ┌───────────────┐ ┌───────────────┐  │ POST /providers/{provider}/webhooks
  │ Stripe module │ │ PayPal module │ │ NoOp · yours  │──┘
  └───────────────┘ └───────────────┘ └───────────────┘
```

| Module | Stack | Role |
|---|---|---|
| `payment-gateway-api` | Java 17, OpenAPI | Canonical contract and validated shared models |
| `payment-gateway-be` | Java 25, Spring Boot 4 | REST API, idempotency, webhooks, provider registry |
| `payment-gateway-providers` | Java 25 | The provider SPI and one module per PSP (`noop`, `paypal`, `stripe`) |

Details and the request flow are in the [architecture documentation](https://labs64.io/docs/modules/payment-gateway/#architecture).

## Using Payment Gateway with AI agents

Payment Gateway gives an agent a payments API that is safe to call more than once.

- **Machine-readable contract.** The OpenAPI v1 spec is served by the running service and lives in [`openapi-payment-gateway-v1.yaml`](payment-gateway-api/src/main/resources/openapi/openapi-payment-gateway-v1.yaml). Generated clients and tool definitions work against it directly.
- **Retries are safe.** Always send an `Idempotency-Key` on `POST /payments/{id}/pay`; an agent that times out and retries gets the original transaction, not a second charge.
- **Least privilege.** Operations require scopes such as `payment:write` and `payment:pay`; give an agent only the scopes its task needs.
- **Discover what a tenant can use.** `GET /payment-definitions` lists supported providers; `GET /payment-providers` lists the tenant's configured ones.
- **Working on the code with an agent?** [`AGENTS.md`](AGENTS.md) and [`CLAUDE.md`](CLAUDE.md) describe the layout, conventions and test commands.

## The Labs64.IO Ecosystem

Payment Gateway is one module of the [Labs64.IO Ecosystem](https://labs64.io); [every module is documented here](https://labs64.io/docs/).

| Module | What it does | Link |
|---|---|---|
| **AuditFlow** | Audit-event routing pipeline | [labs64.io-auditflow](https://github.com/Labs64/labs64.io-auditflow) |
| **API Gateway** | Authenticating, policy-enforcing edge for all modules | [labs64.io-authproxy](https://github.com/Labs64/labs64.io-authproxy) |
| **Payment Gateway** | One API over multiple payment providers | [labs64.io-payment-gateway](https://github.com/Labs64/labs64.io-payment-gateway) |
| **Checkout** | Embeddable, brandable checkout | [labs64.io-checkout](https://github.com/Labs64/labs64.io-checkout) |
| **Customer Portal** | Customer self-service UI | [labs64.io-customer-portal](https://github.com/Labs64/labs64.io-customer-portal) |
| **Commons** | Shared Java and Python libraries | [labs64.io-commons](https://github.com/Labs64/labs64.io-commons) |
| **Helm Charts** | Kubernetes deployment for every module | [labs64.io-helm-charts](https://github.com/Labs64/labs64.io-helm-charts) |
| **Workspace** | Dev container and multi-repo tooling | [labs64.io-workspace](https://github.com/Labs64/labs64.io-workspace) |
| **Tests** | Cross-module regression suite | [labs64.io-tests](https://github.com/Labs64/labs64.io-tests) |
| **Docs** | Published documentation | [labs64.io-docs](https://github.com/Labs64/labs64.io-docs) |
| **NetLicensing** | Licensing-as-a-Service: license and entitlement management | [netlicensing.io](https://netlicensing.io) |
| **GuideChimp** | Interactive guided product tours | [labs64.com/guidechimp](https://www.labs64.com/guidechimp/) |

## Contributing

Contributions of all sizes are welcome. Fork, branch, run the tests described in [`AGENTS.md`](AGENTS.md), and open a pull request; to add a payment provider, start with the SPI in [`payment-gateway-providers/providers-spi`](payment-gateway-providers/providers-spi/). If you are unsure whether something fits, open an issue first.

## License

The core of the *Labs64.IO Ecosystem* is entirely open source and free forever. Community modules are licensed under [Apache License 2.0](LICENSE).

---

<p align="center">
  <a href="https://labs64.io"><img alt="Labs64.IO" src="https://labs64.io/docs/assets/images/labs64-logo.png" width="110"></a><br>
  <sub>Made by the <a href="https://labs64.io">Labs64</a> team and community.</sub>
</p>

---

<p align="center">
  <a href="https://labs64.io"><img alt="Labs64.IO" src="https://labs64.io/docs/assets/images/labs64-logo.png" width="110"></a><br>
  <sub>Made by the <a href="https://labs64.io">Labs64</a> team and community.</sub>
</p>
