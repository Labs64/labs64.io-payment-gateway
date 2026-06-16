package io.labs64.paymentgateway.idempotency;

import org.springframework.http.HttpHeaders;

public record IdempotencyResponse(
        int status,
        HttpHeaders headers,
        Object body) {
}
