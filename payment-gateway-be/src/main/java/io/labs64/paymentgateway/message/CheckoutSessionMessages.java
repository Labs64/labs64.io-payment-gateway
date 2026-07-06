package io.labs64.paymentgateway.message;

import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CheckoutSessionMessages {

    private final Messages msg;

    public String notFound(final UUID id) {
        return msg.get("checkout_session.not_found", id);
    }

    public String transactionRequired() {
        return msg.get("checkout_session.transaction_required");
    }

    public String paymentRequired() {
        return msg.get("checkout_session.payment_required");
    }
}
