package io.labs64.paymentgateway.psp.providers.noop;

import java.util.Map;

import io.labs64.paymentgateway.psp.spi.Payment;
import io.labs64.paymentgateway.psp.spi.PaymentContext;
import io.labs64.paymentgateway.psp.spi.PaymentProvider;
import io.labs64.paymentgateway.psp.spi.PaymentResult;
import io.labs64.paymentgateway.psp.spi.PaymentTransaction;
import io.labs64.paymentgateway.psp.spi.PaymentTransactionStatus;
import io.labs64.paymentgateway.psp.spi.StatusDetails;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * No-Op payment provider for testing purposes.
 * Always returns a successful synchronous payment result.
 */
@Component
public class NoopPaymentProvider implements PaymentProvider {

    private static final Logger log = LoggerFactory.getLogger(NoopPaymentProvider.class);

    @Override
    public String provider() {
        return "noop";
    }

    @Override
    public PaymentResult execute(final PaymentContext context) {
        final Payment payment = context.payment();
        final PaymentTransaction transaction = context.transaction();

        log.info("Noop PSP: Executing payment for paymentId={}, transaction={}", payment.id(), transaction.id());

        final StatusDetails details = new StatusDetails("SUCCESS", "TBD");

        return new PaymentResult(provider(), PaymentTransactionStatus.SUCCESS, Map.of(), details, null);
    }
}
