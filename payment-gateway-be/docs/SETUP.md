# Setup

## Prerequisites
- Java 21
- Maven
- Docker (optional for local stack)

## Local development
1. Start dependencies:
   - `docker compose up -d postgres redis rabbitmq`
2. Run migrations automatically on app start.
3. Start the app:
   - `mvn spring-boot:run`

## Health check
- `GET /actuator/health`
