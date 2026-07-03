package io.labs64.paymentgateway.idempotency;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class IdempotencyOperationTest {

    @Test
    void keyCombinesMethodAndPathPattern() {
        assertThat(new IdempotencyOperation("POST", "/api/v1/payments/{paymentId}/pay").key())
                .isEqualTo("POST:/api/v1/payments/{paymentId}/pay");
    }
}
