package io.labs64.paymentgateway.service;

import io.labs64.paymentgateway.entity.TenantPspConfig;
import io.labs64.paymentgateway.repository.TenantPspConfigRepository;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TenantPspConfigService {
	private final TenantPspConfigRepository repository;
	private final JsonService jsonService;

	@Transactional
	public void upsertConfig(String tenantId, String provider, Map<String, Object> config) {
		TenantPspConfig entity = repository.findByTenantIdAndProvider(tenantId, provider)
				.orElseGet(TenantPspConfig::new);
		entity.setTenantId(tenantId);
		entity.setProvider(provider);
		entity.setConfig(jsonService.toJson(config));
		repository.save(entity);
	}

	public Map<String, Object> getConfigOrEmpty(String tenantId, String provider) {
		return repository.findByTenantIdAndProvider(tenantId, provider)
				.map(TenantPspConfig::getConfig)
				.map(jsonService::toMap)
				.orElseGet(Map::of);
	}

	public boolean hasConfig(String tenantId, String provider) {
		return repository.findByTenantIdAndProvider(tenantId, provider)
				.map(TenantPspConfig::getConfig)
				.filter(config -> config != null && !config.isBlank() && !jsonService.toMap(config).isEmpty())
				.isPresent();
	}
}
