package io.labs64.paymentgateway.psp;

public interface PaymentProvider {
	String getProviderKey();

	PaymentProviderResult initiate(PaymentProviderContext context);

	PaymentProviderResult execute(PaymentProviderContext context);
}
