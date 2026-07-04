# AGENTS.md — Labs64.IO :: Payment Gateway

Unified payment gateway: consolidates PSPs (Stripe, PayPal, NoOp) into a single API with idempotency and webhooks.

## Ecosystem role

- Receives payment requests from checkout-be and other modules.
- Publishes payment events to RabbitMQ → consumed by auditflow-be.
- PSP webhooks at `WebhookController` → `WebhookService`.
- Swagger UI at `gateway.localhost/payment-gateway/v3/api-docs`.

## Repository layout

| Path | Service | Stack | Port |
|------|---------|-------|------|
| `payment-gateway-be/` | Backend | Java 25, Spring Boot 4.1.0, Maven | 8080 |

## Critical guardrails

1. **OpenAPI-first**: canonical spec at `payment-gateway-be/src/main/resources/openapi/openapi-payment-gateway.yaml`.
2. **Never edit generated Java** under `target/`.
3. **Never hardcode credentials** — env vars or K8s Secrets only.
4. **Preserve `l64user`** (uid/gid 1064) in Dockerfiles.

## Backend details

- **Package**: `io.labs64.paymentgateway`
- **Key services**: `PaymentService`, `PaymentTransactionService`, `PaymentProviderService`, `PaymentDefinitionService`, `PaymentNextActionService`, `WebhookService`, `IdempotencyService`
- **PSP integration**: SPI plugin system under `psp/spi/` — `PaymentProvider` interface, `PaymentProviderRegistry`.
- **Current providers**: `NoopPaymentProvider` (stub). Add new under `psp/providers/<name>/`.
- **Idempotency**: Redis-backed via `IdempotencyInterceptor` + `IdempotencyCleanupScheduler`.
- **Correlation**: `CorrelationTraceService` + `CorrelationContextHolder`.
- **Multi-tenancy**: `TenantHeaderFilter`, `RequestTenantProvider`.
- **Database**: PostgreSQL. **Cache**: Redis (idempotency).

## Build, run, test

```bash
cd payment-gateway-be
just build          # mvn clean package -DskipTests
just test           # mvn clean verify
just unit-test      # mvn test (unit only)
just infra-up       # docker compose up (PostgreSQL + Redis + RabbitMQ)
just run            # build + mvn spring-boot:run -Dspring-boot.run.profiles=local
just infra-down     # stop infrastructure
```

Local URLs: Swagger `:8080/swagger-ui/index.html`, RabbitMQ `:15672`.

## Where to make common changes

| Goal | Where |
|------|-------|
| API contract | `payment-gateway-be/src/main/resources/openapi/openapi-payment-gateway.yaml` |
| Add PSP provider | `payment-gateway-be/src/main/java/.../psp/providers/<name>/` |
| Modify PSP SPI | `payment-gateway-be/src/main/java/.../psp/spi/` |
| Backend service | `payment-gateway-be/src/main/java/.../service/` |
| REST controller | `payment-gateway-be/src/main/java/.../controller/` |
| Idempotency logic | `payment-gateway-be/src/main/java/.../idempotency/` |
| Webhook handling | `payment-gateway-be/src/main/java/.../service/WebhookServiceImpl.java` |
