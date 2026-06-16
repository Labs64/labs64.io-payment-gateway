package io.labs64.paymentgateway.message;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class IdempotencyMessages {

    private final Messages msg;

    public String recordNotInitialized() {
        return msg.get("idempotency.record_not_initialized");
    }

    public String requestHashConflict() {
        return msg.get("idempotency.request_hash_conflict");
    }

    public String stillProcessing() {
        return msg.get("idempotency.still_processing");
    }
}
