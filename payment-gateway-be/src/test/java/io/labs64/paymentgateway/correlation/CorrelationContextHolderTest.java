package io.labs64.paymentgateway.correlation;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CorrelationContextHolderTest {

    @AfterEach
    void tearDown() {
        CorrelationContextHolder.clear();
    }

    @Test
    void getReturnsEmptyWhenCorrelationIdIsMissing() {
        assertThat(CorrelationContextHolder.get()).isEmpty();
    }

    @Test
    void setStoresCorrelationIdInMdc() {
        CorrelationContextHolder.set("correlation-1");

        assertThat(CorrelationContextHolder.get()).contains("correlation-1");
        assertThat(CorrelationContextHolder.require()).isEqualTo("correlation-1");
    }

    @Test
    void blankSetClearsCorrelationId() {
        CorrelationContextHolder.set("correlation-1");

        CorrelationContextHolder.set(" ");

        assertThat(CorrelationContextHolder.get()).isEmpty();
    }

    @Test
    void requireThrowsWhenCorrelationIdIsMissing() {
        assertThatThrownBy(CorrelationContextHolder::require)
                .isInstanceOf(IllegalStateException.class);
    }
}
