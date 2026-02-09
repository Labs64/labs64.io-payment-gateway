package io.labs64.paymentgateway.psp;

import io.labs64.paymentgateway.entity.TransactionStatus;
import io.labs64.paymentgateway.dto.NextActionDto;
import java.util.Map;

public record PaymentProviderResult(
		TransactionStatus transactionStatus,
		NextActionDto nextAction,
		Map<String, Object> pspData) {
}
