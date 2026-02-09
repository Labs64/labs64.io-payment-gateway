package io.labs64.paymentgateway.psp;

import io.labs64.paymentgateway.entity.TransactionStatus;
import io.labs64.paymentgateway.dto.NextActionDto;
import com.paypal.core.PayPalEnvironment;
import com.paypal.core.PayPalHttpClient;
import com.paypal.http.HttpResponse;
import com.paypal.orders.AmountWithBreakdown;
import com.paypal.orders.LinkDescription;
import com.paypal.orders.Order;
import com.paypal.orders.OrderRequest;
import com.paypal.orders.OrdersCaptureRequest;
import com.paypal.orders.OrdersCreateRequest;
import com.paypal.orders.PurchaseUnitRequest;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class PayPalPaymentProvider implements PaymentProvider {

	@Override
	public String getProviderKey() {
		return "paypal";
	}

	@Override
	public PaymentProviderResult initiate(PaymentProviderContext context) {
		Map<String, Object> config = context.tenantConfig();
		PayPalHttpClient client = buildClient(config);
		AmountCurrency amountCurrency = resolveAmount(context.requestData());

		OrderRequest orderRequest = new OrderRequest();
		orderRequest.checkoutPaymentIntent("CAPTURE");
		orderRequest.purchaseUnits(List.of(new PurchaseUnitRequest()
				.amountWithBreakdown(new AmountWithBreakdown()
						.currencyCode(amountCurrency.currency())
						.value(String.valueOf(amountCurrency.amount())))));

		OrdersCreateRequest request = new OrdersCreateRequest();
		request.requestBody(orderRequest);

		try {
			HttpResponse<Order> response = client.execute(request);
			Order order = response.result();
			String approvalUrl = order.links().stream()
					.filter(link -> "approve".equalsIgnoreCase(link.rel()))
					.map(LinkDescription::href)
					.findFirst()
					.orElse(null);
			return new PaymentProviderResult(null,
					new NextActionDto("redirect", Map.of("approvalUrl", approvalUrl, "orderId", order.id())),
					Map.of("orderId", order.id(), "reference", order.id()));
		} catch (Exception ex) {
			throw new IllegalStateException("PayPal initiate failed");
		}
	}

	@Override
	public PaymentProviderResult execute(PaymentProviderContext context) {
		Map<String, Object> config = context.tenantConfig();
		PayPalHttpClient client = buildClient(config);

		Map<String, Object> pspData = asMap(context.requestData().get("pspData"));
		String orderId = String.valueOf(pspData.getOrDefault("orderId", ""));
		if (orderId.isBlank()) {
			throw new IllegalArgumentException("PayPal orderId is required");
		}

		OrdersCaptureRequest request = new OrdersCaptureRequest(orderId);
		request.requestBody(Map.of());
		try {
			HttpResponse<Order> response = client.execute(request);
			Order order = response.result();
			TransactionStatus status = "COMPLETED".equalsIgnoreCase(order.status())
					? TransactionStatus.SUCCESS
					: TransactionStatus.PENDING;
			return new PaymentProviderResult(status, new NextActionDto("none", Map.of()),
					Map.of("orderId", order.id(), "status", order.status(), "reference", order.id()));
		} catch (Exception ex) {
			throw new IllegalStateException("PayPal execution failed");
		}
	}

	private PayPalHttpClient buildClient(Map<String, Object> config) {
		String clientId = getRequiredConfig(config, "clientId");
		String clientSecret = getRequiredConfig(config, "clientSecret");
		String mode = String.valueOf(config.getOrDefault("mode", "sandbox"));
		PayPalEnvironment environment = "live".equalsIgnoreCase(mode)
				? new PayPalEnvironment.Live(clientId, clientSecret)
				: new PayPalEnvironment.Sandbox(clientId, clientSecret);
		return new PayPalHttpClient(environment);
	}

	private AmountCurrency resolveAmount(Map<String, Object> requestData) {
		Map<String, Object> purchaseOrder = asMap(requestData.get("purchaseOrder"));
		Object amountValue = purchaseOrder.get("amount");
		Object currencyValue = purchaseOrder.get("currency");
		if (amountValue == null || currencyValue == null) {
			throw new IllegalArgumentException("purchaseOrder.amount and purchaseOrder.currency are required");
		}
		String amount = amountValue.toString();
		String currency = currencyValue.toString().toUpperCase();
		return new AmountCurrency(amount, currency);
	}

	private String getRequiredConfig(Map<String, Object> config, String key) {
		Object value = config.get(key);
		if (value == null || value.toString().isBlank()) {
			throw new IllegalArgumentException("Missing PayPal config: " + key);
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

	private record AmountCurrency(String amount, String currency) {
	}
}
