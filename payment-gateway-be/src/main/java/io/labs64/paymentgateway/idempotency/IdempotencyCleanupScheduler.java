package io.labs64.paymentgateway.idempotency;

import io.labs64.paymentgateway.service.IdempotencyService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class IdempotencyCleanupScheduler {

    private final IdempotencyService idempotencyService;

    @Scheduled(fixedDelayString = "${payment-gateway.idempotency.cleanup-interval:PT1H}")
    public void cleanupExpired() {
        idempotencyService.cleanupExpired();
    }
}
