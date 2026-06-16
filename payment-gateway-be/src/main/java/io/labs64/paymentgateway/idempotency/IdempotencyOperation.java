package io.labs64.paymentgateway.idempotency;

public record IdempotencyOperation(String method, String pathPattern) {

    public String key() {
        return method + ":" + pathPattern;
    }
}
