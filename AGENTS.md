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
| `payment-gateway-be/` | Backend | Java 25, Spring Boot 4.0.5, Maven | 8080 | REST API, PSP integration, idempotency, webhooks |
| `payment-gateway-fe/` | Frontend | (stub — just a justfile) | — | Placeholder |
| `examples/` | Examples | — | — | Integration examples |

## Critical guardrails

1. **Never edit OpenAPI-generated Java** under `target/`. Change the YAML spec and rebuild.
2. **Never hardcode credentials.** Use environment variables or Kubernetes Secrets.
3. **Preserve non-root user `l64user`** (uid/gid 1064) in all Dockerfiles.
4. **OpenAPI-first**: the canonical spec is at `payment-gateway-be/src/main/resources/openapi/openapi-payment-gateway.yaml`.
5. **Each repo has its own git history** — do not cross-commit between repositories.

## Backend (`payment-gateway-be`) details

- **Build is OpenAPI-first.** Models and API interfaces are generated from `openapi-payment-gateway.yaml` by `openapi-generator-maven-plugin`. Generated sources live under `target/generated-sources` and are git-ignored.
- **Package**: `io.labs64.paymentgateway`
- **Key services**: `PaymentService`, `PaymentTransactionService`, `PaymentProviderService`, `PaymentDefinitionService`, `PaymentNextActionService`, `WebhookService`, `IdempotencyService`
- **Key controllers**: `PaymentController`, `PaymentTransactionController`, `PaymentProviderController`, `PaymentDefinitionController`, `WebhookController`
- **PSP integration**: SPI-based plugin system under `psp/spi/` (`PaymentProvider`, `Payment`, `PaymentContext`, `PaymentResult`, `PaymentTransaction`, `PaymentNextAction`, `WebhookRequest`, `PaymentWebhookResult`).
- **Current providers**: `NoopPaymentProvider` (stub at `psp/providers/noop/`). Add new providers under `psp/providers/<name>/`.
- **Provider registry**: `PaymentProviderRegistry` in `psp/internal/` manages provider lookup.
- **Idempotency**: Redis-backed idempotency with `IdempotencyInterceptor`, `IdempotencyService`, `IdempotencyCleanupScheduler`.
- **Correlation**: `CorrelationTraceService` + `CorrelationContextHolder` for request tracing.
- **Multi-tenancy & security**: trusted gateway auth-context (`X-Auth-*`, `auth-context-spring-boot-starter`); `AuthContextHolder` supplies tenant + roles (dev fallback: `labs64.tenant.default` in the `local` profile). Path-level RBAC is enforced at the Traefik gateway; PSP webhooks / redirect returns / payment-definitions are public paths (webhook authenticity = PSP signature checks).
- **Cross-cutting**: `CorrelationIdFilter`, `GlobalExceptionHandler`, `CorsConfig`, `FasterxmlJacksonConfig`.
- **Observability**: Actuator + Micrometer Tracing (OTLP/HTTP) + Prometheus scrape at `/actuator/prometheus`.
- **Database**: PostgreSQL (payments, transactions, providers).
- **Cache**: Redis (idempotency).

### Dockerfile

- Base: `eclipse-temurin:25-alpine`
- Non-root user: `l64user` (uid/gid 1064)
- Healthcheck: `/actuator/health/liveness`
- Entrypoint: `java ${JAVA_OPTS} -jar app.jar`

## Build, run, test

```bash
cd payment-gateway-be
just build              # mvn clean package -DskipTests
just test               # mvn clean verify
just unit-test          # mvn test (unit tests only)
just infra-up           # docker compose up (PostgreSQL + Redis + RabbitMQ)
just run                # build + mvn spring-boot:run -Dspring-boot.run.profiles=local
just infra-down         # stop infrastructure
just infra-reset        # stop + remove volumes
just docu               # open Swagger UI
```

Local URLs: backend Swagger `http://localhost:8080/swagger-ui/index.html`, RabbitMQ UI `http://localhost:15672`.

## Conventions

- **Java 25** and **Maven 3.6.3+** enforced by `maven-enforcer-plugin`.
- **Spring Boot 4.0.5** with Spring Cloud 2025.x. Use reactive WebClient for HTTP calls.
- **Credentials from environment variables only** — never hardcode, never commit defaults.
- Backend tests: JUnit 5 + Spring Boot Test alongside source in `src/test/java/`.
- All Dockerfiles run as non-root user `l64user` (uid/gid 1064).
- Logging: backend uses SLF4J/Logback with logstash JSON encoder.

## Where to make common changes

| Goal | Where |
|------|-------|
| Change the API contract | `payment-gateway-be/src/main/resources/openapi/openapi-payment-gateway.yaml` |
| Add a PSP provider | `payment-gateway-be/src/main/java/io/labs64/paymentgateway/psp/providers/<name>/` |
| Modify PSP SPI | `payment-gateway-be/src/main/java/io/labs64/paymentgateway/psp/spi/` |
| Add a backend service | `payment-gateway-be/src/main/java/io/labs64/paymentgateway/service/` |
| Add a REST controller | `payment-gateway-be/src/main/java/io/labs64/paymentgateway/controller/` |
| Modify idempotency logic | `payment-gateway-be/src/main/java/io/labs64/paymentgateway/idempotency/` |
| Change webhook handling | `payment-gateway-be/src/main/java/io/labs64/paymentgateway/service/WebhookServiceImpl.java` |
