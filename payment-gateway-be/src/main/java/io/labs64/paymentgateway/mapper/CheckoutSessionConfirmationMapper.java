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
        return CheckoutSessionConfirmation.builder()
                .sessionId(entity.getId())
                .payment(toPayment(entity.getPayment()))
                .paymentTransaction(toPaymentTransaction(entity.getPaymentTransaction()))
                .build();
    }

    private ConfirmationPayment toPayment(final PaymentEntity entity) {
        return ConfirmationPayment.builder()
                .id(entity.getId())
                .provider(provider(entity.getPaymentProvider()))
                .status(entity.getStatus())
                .type(entity.getType())
                .description(entity.getDescription())
                .amount(amount(entity.getPurchaseOrder()))
                .currency(currency(entity.getPurchaseOrder()))
                .createdAt(entity.getCreatedAt())
                .build();
    }

    private ConfirmationPaymentTransaction toPaymentTransaction(final PaymentTransactionEntity entity) {
        return ConfirmationPaymentTransaction.builder()
                .id(entity.getId())
                .status(entity.getStatus())
                .statusDetails(entity.getStatusDetails())
                .createdAt(entity.getCreatedAt())
                .build();
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
            try {
                return Long.valueOf(stringValue);
            } catch (NumberFormatException ex) {
                return null;
            }
        }
        return null;
    }
}
