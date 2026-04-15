package io.labs64.paymentgateway.psp.noop;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.labs64.paymentgateway.entity.PaymentEntity;
import io.labs64.paymentgateway.entity.TransactionEntity.TransactionStatus;
import io.labs64.paymentgateway.psp.PspPaymentResponse;
import io.labs64.paymentgateway.psp.PspWebhookResult;

class NoOpPaymentProviderTest {

    private NoOpPaymentProvider provider;

    @BeforeEach
    void setUp() {
        provider = new NoOpPaymentProvider();
    }

    @Test
    void testExecutePayment() {
        PaymentEntity payment = new PaymentEntity();
        payment.setId(UUID.randomUUID());
        payment.setAmount(1000L);
        payment.setCurrency("USD");

        PspPaymentResponse response = provider.executePayment(payment, Map.of());

        assertNotNull(response);
        assertEquals(TransactionStatus.SUCCESS, response.getStatus());
        assertEquals("NONE", response.getNextAction().getType());
        assertTrue(response.getPspData().containsKey("referenceId"));
    }

    @Test
    void testVerifyWebhook_WithPaymentId() {
        UUID paymentId = UUID.randomUUID();
        Map<String, Object> payload = Map.of(
                "paymentId", paymentId.toString(),
                "status", "SUCCESS"
        );

        PspWebhookResult result = provider.verifyWebhook(payload, Map.of());

        assertTrue(result.isValid());
        assertEquals(paymentId, result.getPaymentId());
        assertEquals(TransactionStatus.SUCCESS, result.getTransactionStatus());
    }
}
