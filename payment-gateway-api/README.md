# Labs64.IO Payment Gateway API

Shared, OpenAPI-first Java contract for the Labs64.IO Payment Gateway.

The module publishes:

- Java 17-compatible validated models generated from the canonical OpenAPI document;
- the OpenAPI document bundled at `openapi/openapi-payment-gateway-v1.yaml`;
- the same document as the `openapi` Maven classifier in release builds.

It also provides a synchronous, thread-safe Java HTTP client whose public API is
independent from OpenAPI Generator implementation details.

## Dependency

```xml
<dependency>
    <groupId>io.labs64</groupId>
    <artifactId>payment-gateway-api</artifactId>
    <version>0.0.1-SNAPSHOT</version>
</dependency>
```

Generated models are available under:

```text
io.labs64.paymentgateway.model
```


## Client

```java
PaymentGatewayClient client = PaymentGatewayClient.builder()
        .baseUrl(URI.create("http://gateway/payment-gateway/api/v1"))
        .accessTokenProvider(serviceTokenProvider)
        .correlationProvider(correlationProvider)
        .build();

PaymentProviderQuery query = PaymentProviderQuery.builder()
        .currency("EUR")
        .country("DE")
        .active(true)
        .page(0)
        .pageSize(20)
        .build();

PagedResult<PaymentProvider> providers = client.paymentProviders().list(query);
```

`CallOptions` can override the correlation id and call timeout and can add an idempotency key.
List filters use query objects, so future optional filters do not require new method overloads.

## Build

From the repository root:

```bash
mvn -pl payment-gateway-api -am clean verify
```

To build the release artifacts, including sources, Javadocs, the classified
OpenAPI document, and signatures:

```bash
mvn -pl payment-gateway-api -Prelease clean verify
```

## Contract ownership

The canonical OpenAPI document lives in this module. The backend consumes it to
generate Spring API interfaces and depends on this artifact for the shared
models, avoiding a second set of generated model classes.

During its `initialize` phase, the backend extracts the bundled OpenAPI document
from this Maven artifact, so it does not depend on a sibling source checkout.
