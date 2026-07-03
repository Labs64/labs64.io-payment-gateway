package io.labs64.paymentgateway.mapper;

import io.labs64.paymentgateway.entity.PaymentEntity;
import io.labs64.paymentgateway.entity.PaymentProviderEntity;
import io.labs64.paymentgateway.entity.CheckoutSessionEntity;
import io.labs64.paymentgateway.entity.PaymentTransactionEntity;
import io.labs64.paymentgateway.psp.spi.Payment;
import io.labs64.paymentgateway.psp.spi.PaymentContext;
import io.labs64.paymentgateway.psp.spi.PaymentExecutionRequest;
import io.labs64.paymentgateway.psp.spi.CheckoutSession;
import io.labs64.paymentgateway.psp.spi.CheckoutPreparationContext;
import io.labs64.paymentgateway.psp.spi.PaymentTransaction;
import io.labs64.paymentgateway.psp.spi.ProviderConfig;
import org.mapstruct.Mapper;

@Mapper(config = MapperConfigBase.class)
public interface PaymentContextMapper {

    Payment toPayment(PaymentEntity entity);

    PaymentTransaction toPaymentTransaction(PaymentTransactionEntity entity);

    ProviderConfig toProviderConfig(PaymentProviderEntity entity);

    CheckoutSession toCheckoutSession(CheckoutSessionEntity entity);

    default PaymentContext toContext(
            final PaymentEntity payment,
            final PaymentTransactionEntity transaction,
            final PaymentProviderEntity provider,
            final CheckoutSessionEntity session,
            final PaymentExecutionRequest request) {
        return new PaymentContext(
                toPayment(payment),
                toPaymentTransaction(transaction),
                toProviderConfig(provider),
                toCheckoutSession(session),
                request);
    }

    default CheckoutPreparationContext toCheckoutPreparationContext(
            final PaymentEntity payment,
            final PaymentTransactionEntity transaction,
            final PaymentProviderEntity provider,
            final PaymentExecutionRequest request) {
        return new CheckoutPreparationContext(
                toPayment(payment),
                toPaymentTransaction(transaction),
                toProviderConfig(provider),
                request);
    }
}
