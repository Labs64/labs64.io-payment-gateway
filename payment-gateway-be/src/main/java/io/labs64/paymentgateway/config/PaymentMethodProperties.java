package io.labs64.paymentgateway.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Data
@Validated
@ConfigurationProperties(prefix = "payment")
public class PaymentMethodProperties {
	private List<PaymentMethodConfig> methods = new ArrayList<>();

	@Data
	public static class PaymentMethodConfig {
		@NotBlank
		private String id;
		@NotBlank
		private String name;
		@NotBlank
		private String description;
		private String icon;
		@NotBlank
		private String provider;
		@NotNull
		private Boolean recurring;
		private List<String> currencies = new ArrayList<>();
		private List<String> countries = new ArrayList<>();
	}
}
