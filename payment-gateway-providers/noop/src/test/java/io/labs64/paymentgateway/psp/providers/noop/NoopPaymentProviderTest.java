package io.labs64.paymentgateway.psp.providers.noop;

import java.util.Map;
import java.util.UUID;

import io.labs64.paymentgateway.psp.spi.Payment;
import io.labs64.paymentgateway.psp.spi.PaymentContext;
import io.labs64.paymentgateway.psp.spi.PaymentResult;
import io.labs64.paymentgateway.psp.spi.PaymentTransaction;
import io.labs64.paymentgateway.psp.spi.PaymentTransactionStatus;
import io.labs64.paymentgateway.psp.spi.PaymentType;
import io.labs64.paymentgateway.psp.spi.ProviderConfig;
import io.labs64.paymentgateway.psp.spi.StatusDetails;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NoopPaymentProviderTest {

    private final NoopPaymentProvider provider = new NoopPaymentProvider();

    @Test
    void providerReturnsNoopIdentifier() {
        assertThat(provider.provider()).isEqualTo("noop");
    }

    @Test
    void executeReturnsSuccessfulSynchronousPaymentResult() {
        final PaymentContext context = new PaymentContext(payment(), transaction(), providerConfig());

        final PaymentResult result = provider.execute(context);

        assertThat(result.provider()).isEqualTo("noop");
        assertThat(result.status()).isEqualTo(PaymentTransactionStatus.SUCCESS);
        assertThat(result.pspData()).isEmpty();
        assertThat(result.statusDetails()).isEqualTo(new StatusDetails("SUCCESS", "TBD"));
        assertThat(result.nextAction()).isNull();
    }

    private static Payment payment() {
        return new Payment(
                UUID.randomUUID(),
                PaymentType.ONE_TIME,
                "Test payment",
                null,
                Map.of("currency", "USD", "grossAmount", 3000L),
                Map.of("email", "customer@example.com"),
                null,
                Map.of("source", "test"));
    }

    private static PaymentTransaction transaction() {
        return new PaymentTransaction(UUID.randomUUID(), PaymentTransactionStatus.PENDING);
    }

    private static ProviderConfig providerConfig() {
        return new ProviderConfig("noop", Map.of(), "Noop", "No operation provider");
    }
}
