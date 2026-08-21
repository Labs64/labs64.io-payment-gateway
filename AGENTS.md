# AGENTS.md — Labs64.IO :: Payment Gateway

Guidance for AI agents working in this repository. Read this before making changes.

## What this project is

Unified payment gateway for the Labs64.IO ecosystem. Consolidates multiple Payment Service Providers (Stripe, PayPal, NoOp) into a single cohesive API. Supports idempotency, webhook processing, and PSP routing.

### Ecosystem role

- Receives payment requests from checkout-be and other modules.
- Publishes payment events to RabbitMQ → consumed by `auditflow-be` for audit logging.
- PSP webhooks arrive at `WebhookController` → processed by `WebhookService`.
- Swagger UI at `gateway.localhost/payment-gateway/v3/api-docs` aggregates the API docs.

## Repository layout

| Path | Service | Stack | Port | Role |
|------|---------|-------|------|------|
| `payment-gateway-api/` | Shared API contract | Java 17, Maven | — | Canonical OpenAPI spec + validated shared models |
| `payment-gateway-be/` | Backend | Java 25, Spring Boot 4.1.0, Maven | 8080 | REST API, idempotency, webhooks, provider registry |
| `payment-gateway-providers/` | PSP modules | Java 25, Maven | — | `providers-spi/` (the SPI) + one module per PSP (`noop/`, `paypal/`, `stripe/`) |
| `payment-gateway-fe/` | Frontend | (stub — just a justfile) | — | Placeholder |
| `tests/` | Tests | Robot Framework | — | Module-level e2e/integration tests |
| `examples/` | Examples | — | — | Integration examples |

## Critical guardrails

1. **Never edit OpenAPI-generated Java** under `target/`. Change the YAML spec and rebuild.
2. **Never hardcode credentials.** Use environment variables or Kubernetes Secrets.
3. **Preserve non-root user `l64user`** (uid/gid 1064) in all Dockerfiles.
4. **OpenAPI-first**: the canonical spec is at `payment-gateway-api/src/main/resources/openapi/openapi-payment-gateway-v1.yaml`.
5. **Each repo has its own git history** — do not cross-commit between repositories.

## Backend (`payment-gateway-be`) details

- **Build is OpenAPI-first.** Models and API interfaces are generated from `openapi-payment-gateway.yaml` by `openapi-generator-maven-plugin`. Generated sources live under `target/generated-sources` and are git-ignored.
- **Shared contract.** `payment-gateway-api` owns the canonical OpenAPI document and generates Java 17-compatible validated models in `io.labs64.paymentgateway.model`. It intentionally does not provide an HTTP client yet.
- **Backend generation.** `payment-gateway-be` depends on `payment-gateway-api` for models and generates only Spring server interfaces from the same contract (`generateModels=false`).
- **Package**: `io.labs64.paymentgateway`
- **Key services**: `PaymentService`, `PaymentTransactionService`, `PaymentProviderService`, `PaymentDefinitionService`, `PaymentNextActionService`, `WebhookService`, `IdempotencyService`
- **Key controllers**: `PaymentController`, `PaymentTransactionController`, `PaymentProviderController`, `PaymentDefinitionController`, `WebhookController`
- **PSP integration**: SPI-based plugin system. The SPI lives in its own module `payment-gateway-providers/providers-spi/` (package `io.labs64.paymentgateway.psp.spi`: `PaymentProvider`, `Payment`, `PaymentContext`, `PaymentResult`, `PaymentTransaction`, `ProviderCheckoutSupport`, `ProviderWebhookSupport`, `PaymentWebhookResult`, …).
- **Current providers**: `NoopPaymentProvider` (`payment-gateway-providers/noop/`), `PaypalPaymentProvider` (`payment-gateway-providers/paypal/`), and `StripePaymentProvider` (`payment-gateway-providers/stripe/`). Add new providers as a new module under `payment-gateway-providers/<name>/`.
- **Provider registry**: `PaymentProviderRegistry` in `payment-gateway-be` `psp/internal/` manages provider lookup.
- **Idempotency**: Redis-backed idempotency with `IdempotencyInterceptor`, `IdempotencyService`, `IdempotencyCleanupScheduler`.
- **Correlation**: `CorrelationTraceService` + `CorrelationContextHolder` for request tracing.
- **Multi-tenancy & security**: trusted gateway auth-context (`X-Auth-*`, `auth-context-spring-boot-starter`); `AuthContextHolder` supplies tenant + roles (dev fallback: `labs64.tenant.default` in the `local` profile). Path-level RBAC is enforced at the Traefik gateway; PSP webhooks / redirect returns / payment-definitions are public paths (webhook authenticity = PSP signature checks).
- **Cross-cutting**: `CorrelationIdFilter`, `GlobalExceptionHandler`, `CorsConfig`, `FasterxmlJacksonConfig`.
- **Observability**: Actuator + Micrometer Tracing (OTLP/HTTP) + Prometheus scrape at `/actuator/prometheus`.
- **Database**: PostgreSQL (payments, transactions, providers).
- **Cache**: Redis (idempotency).

### Dockerfile

- Base: `eclipse-temurin:25-jre`
- Non-root user: `l64user` (uid/gid 1064)
- Healthcheck: `/actuator/health/liveness`
- Entrypoint: `java ${JAVA_OPTS} -jar app.jar`

## Build, run, test

```bash
cd payment-gateway-be
just build              # mvn clean package -DskipTests
just test               # mvn clean verify
just unit-test          # mvn test (unit tests only)
just infra-up           # start PostgreSQL + Redis + RabbitMQ + Cerbos
just run                # build + mvn spring-boot:run -Dspring-boot.run.profiles=local
just infra-down         # stop infrastructure
just infra-reset        # stop infrastructure + remove volumes
just dev-up             # start infrastructure + debug application
just dev-watch          # restart debug application when the JAR changes
just dev-down           # stop the debug Compose stack
just docu               # open Swagger UI
```

Local URLs: backend Swagger `http://localhost:8080/swagger-ui/index.html`, RabbitMQ UI `http://localhost:15672`.

## Conventions

- **Java 25** and **Maven 3.6.3+** enforced by `maven-enforcer-plugin`.
- **Spring Boot 4.1.0** with Spring Cloud 2025.x. Use reactive WebClient for HTTP calls.
- **Credentials from environment variables only** — never hardcode, never commit defaults.
- Backend tests: JUnit 5 + Spring Boot Test alongside source in `src/test/java/`.
- All Dockerfiles run as non-root user `l64user` (uid/gid 1064).
- Logging: backend uses SLF4J/Logback with logstash JSON encoder.

## Where to make common changes

| Goal | Where |
|------|-------|
| Change the API contract | `payment-gateway-api/src/main/resources/openapi/openapi-payment-gateway-v1.yaml` |
| Add a PSP provider | New Maven module under `payment-gateway-providers/<name>/` (see `noop/` / `paypal/`) |
| Modify PSP SPI | `payment-gateway-providers/providers-spi/src/main/java/io/labs64/paymentgateway/psp/spi/` |
| Add a backend service | `payment-gateway-be/src/main/java/io/labs64/paymentgateway/service/` |
| Add a REST controller | `payment-gateway-be/src/main/java/io/labs64/paymentgateway/controller/` |
| Modify idempotency logic | `payment-gateway-be/src/main/java/io/labs64/paymentgateway/idempotency/` |
| Change webhook handling | `payment-gateway-be/src/main/java/io/labs64/paymentgateway/service/WebhookServiceImpl.java` |
