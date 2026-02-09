package io.labs64.paymentgateway.service;

import io.labs64.paymentgateway.config.PaymentMethodProperties;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class PaymentMethodServiceTest {

	@Test
	void filtersMethodsByCurrencyAndCountry() {
		PaymentMethodProperties.PaymentMethodConfig config = new PaymentMethodProperties.PaymentMethodConfig();
		config.setId("card");
		config.setName("Card");
		config.setDescription("Card payments");
		config.setProvider("stripe");
		config.setRecurring(true);
		config.setCurrencies(List.of("USD", "EUR"));
		config.setCountries(List.of("US"));

		PaymentMethodProperties properties = new PaymentMethodProperties();
		properties.setMethods(List.of(config));

		TenantPspConfigService tenantPspConfigService = Mockito.mock(TenantPspConfigService.class);
		Mockito.when(tenantPspConfigService.hasConfig("tenant-1", "stripe")).thenReturn(true);
		PaymentMethodService service = new PaymentMethodService(properties, tenantPspConfigService);

		Assertions.assertEquals(1, service.getPaymentMethods(
				"tenant-1", java.util.Optional.of("USD"), java.util.Optional.of("US")).size());
		Assertions.assertEquals(0, service.getPaymentMethods(
				"tenant-1", java.util.Optional.of("GBP"), java.util.Optional.of("US")).size());
	}
}
