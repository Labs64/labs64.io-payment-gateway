package io.labs64.paymentgateway.mapper;

import io.labs64.paymentgateway.entity.PaymentEntity;
import io.labs64.paymentgateway.entity.PaymentProviderEntity;
import io.labs64.paymentgateway.entity.PaymentTransactionEntity;
import io.labs64.paymentgateway.psp.spi.Payment;
import io.labs64.paymentgateway.psp.spi.PaymentContext;
import io.labs64.paymentgateway.psp.spi.PaymentTransaction;
import io.labs64.paymentgateway.psp.spi.ProviderConfig;
import org.mapstruct.Mapper;

@Mapper(config = MapperConfigBase.class)
public interface PaymentContextMapper {

    Payment toPayment(PaymentEntity entity);

    PaymentTransaction toPaymentTransaction(PaymentTransactionEntity entity);

    ProviderConfig toProviderConfig(PaymentProviderEntity entity);

    default PaymentContext toContext(
            final PaymentEntity payment,
            final PaymentTransactionEntity transaction,
            final PaymentProviderEntity provider) {
        return new PaymentContext(
                toPayment(payment),
                toPaymentTransaction(transaction),
                toProviderConfig(provider));
    }
}
