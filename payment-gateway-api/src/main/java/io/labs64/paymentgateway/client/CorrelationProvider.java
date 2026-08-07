package io.labs64.paymentgateway.client;

import java.util.Optional;

/** Supplies a correlation id when it is not specified in {@link CallOptions}. */
@FunctionalInterface
public interface CorrelationProvider {

    Optional<String> correlationId();
}
