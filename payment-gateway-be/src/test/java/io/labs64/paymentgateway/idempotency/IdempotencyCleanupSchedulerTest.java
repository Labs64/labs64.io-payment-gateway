package io.labs64.paymentgateway.idempotency;

import io.labs64.paymentgateway.service.IdempotencyService;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class IdempotencyCleanupSchedulerTest {

    @Test
    void cleanupExpiredDelegatesToService() {
        final IdempotencyService service = mock(IdempotencyService.class);
        final IdempotencyCleanupScheduler scheduler = new IdempotencyCleanupScheduler(service);

        scheduler.cleanupExpired();

        verify(service).cleanupExpired();
    }
}
