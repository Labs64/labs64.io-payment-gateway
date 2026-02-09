package io.labs64.paymentgateway.psp;

import io.labs64.paymentgateway.entity.TransactionStatus;
import io.labs64.paymentgateway.dto.NextActionDto;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class NoOpPaymentProvider implements PaymentProvider {
	@Override
	public String getProviderKey() {
		return "noop";
	}

	@Override
	public PaymentProviderResult initiate(PaymentProviderContext context) {
		return new PaymentProviderResult(null, new NextActionDto("none", Map.of()), Map.of("noop", true));
	}

	@Override
	public PaymentProviderResult execute(PaymentProviderContext context) {
		return new PaymentProviderResult(TransactionStatus.SUCCESS, new NextActionDto("none", Map.of()),
				Map.of("noop", true, "message", "NoOp provider executed."));
	}
}
