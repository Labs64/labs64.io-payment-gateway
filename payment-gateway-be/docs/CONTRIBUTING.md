# Contributing

## Development workflow
- Follow Java 21 + Spring Boot 3.x conventions.
- Keep logs free of sensitive data (no PAN/PII/credentials).
- Add unit tests for new logic.

## Running tests
- `mvn test`

## Code style
- Prefer small, focused classes.
- Keep PSP adapters isolated behind the `PaymentProvider` interface.
