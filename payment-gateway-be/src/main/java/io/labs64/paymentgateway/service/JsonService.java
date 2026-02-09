package io.labs64.paymentgateway.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Collections;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class JsonService {
	private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
	};

	private final ObjectMapper objectMapper;

	public String toJson(Object value) {
		if (value == null) {
			return null;
		}
		try {
			return objectMapper.writeValueAsString(value);
		} catch (Exception ex) {
			throw new IllegalStateException("Failed to serialize JSON", ex);
		}
	}

	public Map<String, Object> toMap(String json) {
		if (json == null || json.isBlank()) {
			return Collections.emptyMap();
		}
		try {
			return objectMapper.readValue(json, MAP_TYPE);
		} catch (Exception ex) {
			throw new IllegalStateException("Failed to deserialize JSON", ex);
		}
	}
}
