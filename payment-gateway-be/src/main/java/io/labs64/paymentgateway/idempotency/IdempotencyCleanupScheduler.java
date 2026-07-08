package io.labs64.paymentgateway.idempotency;

import io.labs64.paymentgateway.service.IdempotencyService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

@Component
@EnableScheduling
@ConditionalOnProperty(name="spring.main.web-application-type", havingValue="servlet", matchIfMissing=true)
@RequiredArgsConstructor
public class IdempotencyCleanupScheduler {

    private static final Logger log = LoggerFactory.getLogger(IdempotencyCleanupScheduler.class);

    private final IdempotencyService idempotencyService;

    @Scheduled(fixedDelayString = "${payment-gateway.idempotency.cleanup-interval:PT1H}")
    public void cleanupExpired() {
        try {
            log.debug("Running idempotency cleanup");
            idempotencyService.cleanupExpired();
        } catch (Exception e) {
            log.error("Idempotency cleanup failed", e);
        }
    }
}
