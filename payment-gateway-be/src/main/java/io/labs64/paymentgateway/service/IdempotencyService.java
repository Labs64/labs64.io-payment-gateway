package io.labs64.paymentgateway.service;

import io.labs64.paymentgateway.idempotency.IdempotencyContext;
import io.labs64.paymentgateway.idempotency.IdempotencyResponse;

import java.util.Optional;

public interface IdempotencyService {

    Optional<IdempotencyResponse> startOrReplay(IdempotencyContext context);

    void complete(IdempotencyContext context, IdempotencyResponse response);

    void cleanupExpired();
}
