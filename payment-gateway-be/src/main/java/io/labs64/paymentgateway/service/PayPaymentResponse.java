package io.labs64.paymentgateway.service;

import io.labs64.paymentgateway.entity.PaymentEntity;
import io.labs64.paymentgateway.entity.PaymentTransactionEntity;
import io.labs64.paymentgateway.psp.spi.PaymentNextAction;

public record PayPaymentResponse(PaymentEntity payment, PaymentTransactionEntity transaction, PaymentNextAction nextAction) {
}
