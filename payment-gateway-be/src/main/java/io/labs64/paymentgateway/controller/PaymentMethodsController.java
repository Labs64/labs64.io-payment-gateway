package io.labs64.paymentgateway.controller;

import io.labs64.paymentgateway.dto.ConfigurePspRequest;
import io.labs64.paymentgateway.dto.PaymentMethodDto;
import io.labs64.paymentgateway.service.PaymentMethodService;
import io.labs64.paymentgateway.security.TenantResolver;
import io.labs64.paymentgateway.service.TenantPspConfigService;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("payment-methods")
@RequiredArgsConstructor
public class PaymentMethodsController {
	private static final Logger logger = LoggerFactory.getLogger(PaymentMethodsController.class);

	private final PaymentMethodService paymentMethodService;
	private final TenantPspConfigService tenantPspConfigService;
	private final TenantResolver tenantResolver;

	@GetMapping()
	public ResponseEntity<List<PaymentMethodDto>> getPaymentMethods(
			@RequestParam Optional<String> currency,
			@RequestParam Optional<String> country) {
		String tenantId = tenantResolver.resolveTenantId();
		return ResponseEntity.ok(paymentMethodService.getPaymentMethods(tenantId, currency, country));
	}

	@PostMapping("/{paymentMethodId}")
	public ResponseEntity<Void> configurePaymentMethod(@PathVariable String paymentMethodId,
			@RequestBody ConfigurePspRequest request) {
		String tenantId = tenantResolver.resolveTenantId();
		String provider = paymentMethodService.getMethodById(paymentMethodId).getProvider();
		tenantPspConfigService.upsertConfig(tenantId, provider, request.pspConfig());
		logger.info("Tenant PSP config updated tenant={} method={}", tenantId, paymentMethodId);
		return ResponseEntity.noContent().build();
	}
}
