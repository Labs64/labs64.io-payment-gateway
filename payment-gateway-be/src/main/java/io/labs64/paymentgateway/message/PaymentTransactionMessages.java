package io.labs64.paymentgateway.message;

import java.util.UUID;

import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class PaymentTransactionMessages {

    private final Messages msg;

    public String notFound(final UUID id) {
        return msg.get("payment_transaction.not_found", id);
    }

    public String required() {
        return msg.get("payment_transaction.required");
    }

    public String paymentRequired() {
        return msg.get("payment_transaction.payment_required");
    }
}
