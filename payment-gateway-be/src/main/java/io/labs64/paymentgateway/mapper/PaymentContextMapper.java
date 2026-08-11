package io.labs64.paymentgateway.mapper;

import io.labs64.paymentgateway.entity.CheckoutSessionEntity;
import io.labs64.paymentgateway.entity.PaymentEntity;
import io.labs64.paymentgateway.entity.PaymentProviderEntity;
import io.labs64.paymentgateway.entity.PaymentTransactionEntity;
import io.labs64.paymentgateway.psp.spi.CheckoutPreparationContext;
import io.labs64.paymentgateway.psp.spi.CheckoutSession;
import io.labs64.paymentgateway.psp.spi.Payment;
import io.labs64.paymentgateway.psp.spi.PaymentContext;
import io.labs64.paymentgateway.psp.spi.PaymentExecutionRequest;
import io.labs64.paymentgateway.psp.spi.PaymentTransaction;
import io.labs64.paymentgateway.psp.spi.ProviderCheckout;
import io.labs64.paymentgateway.psp.spi.ProviderConfig;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(config = MapperConfigBase.class, uses = PaymentJsonMapper.class)
public interface PaymentContextMapper {

    @Mapping(target = "recurrence", source = "recurrence", qualifiedByName = "mapToRecurrence")
    @Mapping(target = "purchaseOrder", source = "purchaseOrder", qualifiedByName = "mapToPurchaseOrder")
    @Mapping(target = "billingInfo", source = "billingInfo", qualifiedByName = "mapToBillingInfo")
    @Mapping(target = "shippingInfo", source = "shippingInfo", qualifiedByName = "mapToShippingInfo")
    Payment toPayment(PaymentEntity entity);

    default io.labs64.paymentgateway.psp.spi.PaymentType toProviderPaymentType(
            final io.labs64.paymentgateway.model.PaymentType type) {
        return type == null
                ? null
                : io.labs64.paymentgateway.psp.spi.PaymentType.valueOf(type.name());
    }

    PaymentTransaction toPaymentTransaction(PaymentTransactionEntity entity);

    default io.labs64.paymentgateway.psp.spi.PaymentTransactionStatus toProviderTransactionStatus(
            final io.labs64.paymentgateway.model.PaymentTransactionStatus status) {
        return status == null
                ? null
                : io.labs64.paymentgateway.psp.spi.PaymentTransactionStatus.valueOf(status.name());
    }

    static io.labs64.paymentgateway.model.PaymentTransactionStatus toModelTransactionStatus(
            final io.labs64.paymentgateway.psp.spi.PaymentTransactionStatus status) {
        return status == null
                ? null
                : io.labs64.paymentgateway.model.PaymentTransactionStatus.valueOf(status.name());
    }

    ProviderConfig toProviderConfig(PaymentProviderEntity entity);

    CheckoutSession toCheckoutSession(CheckoutSessionEntity entity);

    default PaymentContext toContext(
            final PaymentEntity payment,
            final PaymentTransactionEntity transaction,
            final PaymentProviderEntity provider,
            final PaymentExecutionRequest request,
            final ProviderCheckout checkout) {
        return new PaymentContext(
                toPayment(payment),
                toPaymentTransaction(transaction),
                toProviderConfig(provider),
                request,
                checkout);
    }

    default CheckoutPreparationContext toCheckoutPreparationContext(
            final PaymentEntity payment,
            final PaymentProviderEntity provider,
            final PaymentExecutionRequest request) {
        return new CheckoutPreparationContext(
                toPayment(payment),
                toProviderConfig(provider),
                request);
    }
}
