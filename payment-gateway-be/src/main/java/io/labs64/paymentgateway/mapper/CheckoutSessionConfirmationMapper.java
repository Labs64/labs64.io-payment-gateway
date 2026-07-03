package io.labs64.paymentgateway.mapper;

import java.util.Map;

import io.labs64.paymentgateway.entity.CheckoutSessionEntity;
import io.labs64.paymentgateway.entity.PaymentEntity;
import io.labs64.paymentgateway.entity.PaymentProviderEntity;
import io.labs64.paymentgateway.entity.PaymentTransactionEntity;
import io.labs64.paymentgateway.model.CheckoutSessionConfirmation;
import io.labs64.paymentgateway.model.ConfirmationPayment;
import io.labs64.paymentgateway.model.ConfirmationPaymentTransaction;
import org.springframework.stereotype.Component;

/**
 * Maps checkout session aggregates to public-safe confirmation DTOs.
 */
@Component
public class CheckoutSessionConfirmationMapper {

    private static final String CURRENCY = "currency";
    private static final String GROSS_AMOUNT = "grossAmount";

    public CheckoutSessionConfirmation toDto(final CheckoutSessionEntity entity) {
        final CheckoutSessionConfirmation response = new CheckoutSessionConfirmation();
        response.setSessionId(entity.getId());
        response.setPayment(toPayment(entity.getPayment()));
        response.setPaymentTransaction(toPaymentTransaction(entity.getPaymentTransaction()));
        return response;
    }

    private ConfirmationPayment toPayment(final PaymentEntity entity) {
        final ConfirmationPayment payment = new ConfirmationPayment();
        payment.setId(entity.getId());
        payment.setProvider(provider(entity.getPaymentProvider()));
        payment.setStatus(entity.getStatus());
        payment.setType(entity.getType());
        payment.setDescription(entity.getDescription());
        payment.setAmount(amount(entity.getPurchaseOrder()));
        payment.setCurrency(currency(entity.getPurchaseOrder()));
        payment.setCreatedAt(entity.getCreatedAt());
        return payment;
    }

    private ConfirmationPaymentTransaction toPaymentTransaction(final PaymentTransactionEntity entity) {
        final ConfirmationPaymentTransaction transaction = new ConfirmationPaymentTransaction();
        transaction.setId(entity.getId());
        transaction.setStatus(entity.getStatus());
        transaction.setStatusDetails(entity.getStatusDetails());
        transaction.setCreatedAt(entity.getCreatedAt());
        return transaction;
    }

    private static String provider(final PaymentProviderEntity entity) {
        return entity != null ? entity.getProvider() : null;
    }

    private static String currency(final Map<String, Object> purchaseOrder) {
        final Object value = purchaseOrder != null ? purchaseOrder.get(CURRENCY) : null;
        return value != null ? value.toString() : null;
    }

    private static Long amount(final Map<String, Object> purchaseOrder) {
        final Object value = purchaseOrder != null ? purchaseOrder.get(GROSS_AMOUNT) : null;
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String stringValue && !stringValue.isBlank()) {
            return Long.valueOf(stringValue);
        }
        return null;
    }
}
