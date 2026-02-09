package io.labs64.paymentgateway.psp;

import io.labs64.paymentgateway.entity.TransactionStatus;
import io.labs64.paymentgateway.dto.NextActionDto;
import com.stripe.Stripe;
import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import com.stripe.param.PaymentIntentConfirmParams;
import com.stripe.param.PaymentIntentCreateParams;
import java.util.HashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class StripePaymentProvider implements PaymentProvider {

	@Override
	public String getProviderKey() {
		return "stripe";
	}

	@Override
	public PaymentProviderResult initiate(PaymentProviderContext context) {
		Map<String, Object> config = context.tenantConfig();
		String apiKey = getRequiredConfig(config, "apiKey");
		Stripe.apiKey = apiKey;

		AmountCurrency amountCurrency = resolveAmount(context.requestData());
		PaymentIntentCreateParams params = PaymentIntentCreateParams.builder()
				.setAmount(amountCurrency.amount())
				.setCurrency(amountCurrency.currency())
				.setConfirm(false)
				.build();
		try {
			PaymentIntent intent = PaymentIntent.create(params);
			Map<String, Object> pspData = new HashMap<>();
			pspData.put("paymentIntentId", intent.getId());
			pspData.put("clientSecret", intent.getClientSecret());
			pspData.put("reference", intent.getId());
			return new PaymentProviderResult(null,
					new NextActionDto("stripe-client-secret", Map.of("clientSecret", intent.getClientSecret())),
					pspData);
		} catch (StripeException ex) {
			throw new IllegalStateException("Stripe initiate failed");
		}
	}

	@Override
	public PaymentProviderResult execute(PaymentProviderContext context) {
		Map<String, Object> config = context.tenantConfig();
		String apiKey = getRequiredConfig(config, "apiKey");
		Stripe.apiKey = apiKey;

		Map<String, Object> pspData = asMap(context.requestData().get("pspData"));
		String paymentIntentId = String.valueOf(pspData.getOrDefault("paymentIntentId", ""));
		if (paymentIntentId.isBlank()) {
			throw new IllegalArgumentException("Stripe paymentIntentId is required");
		}
		try {
			PaymentIntent intent = PaymentIntent.retrieve(paymentIntentId);
			if (!"succeeded".equalsIgnoreCase(intent.getStatus())) {
				PaymentIntentConfirmParams confirmParams = PaymentIntentConfirmParams.builder().build();
				intent = intent.confirm(confirmParams);
			}

			TransactionStatus status = switch (intent.getStatus()) {
				case "succeeded" -> TransactionStatus.SUCCESS;
				case "requires_action", "processing" -> TransactionStatus.PENDING;
				default -> TransactionStatus.FAILED;
			};
			Map<String, Object> resultData = new HashMap<>();
			resultData.put("paymentIntentId", intent.getId());
			resultData.put("status", intent.getStatus());
			resultData.put("reference", intent.getId());
			return new PaymentProviderResult(status, new NextActionDto("none", Map.of()), resultData);
		} catch (StripeException ex) {
			throw new IllegalStateException("Stripe execution failed");
		}
	}

	private AmountCurrency resolveAmount(Map<String, Object> requestData) {
		Map<String, Object> purchaseOrder = asMap(requestData.get("purchaseOrder"));
		Object amountValue = purchaseOrder.get("amount");
		Object currencyValue = purchaseOrder.get("currency");
		if (amountValue == null || currencyValue == null) {
			throw new IllegalArgumentException("purchaseOrder.amount and purchaseOrder.currency are required");
		}
		long amount = Long.parseLong(amountValue.toString());
		String currency = currencyValue.toString().toLowerCase();
		return new AmountCurrency(amount, currency);
	}

	private String getRequiredConfig(Map<String, Object> config, String key) {
		Object value = config.get(key);
		if (value == null || value.toString().isBlank()) {
			throw new IllegalArgumentException("Missing Stripe config: " + key);
		}
		return value.toString();
	}

	@SuppressWarnings("unchecked")
	private Map<String, Object> asMap(Object value) {
		if (value instanceof Map<?, ?> map) {
			return (Map<String, Object>) map;
		}
		return Map.of();
	}

	private record AmountCurrency(long amount, String currency) {
	}
}
