package io.labs64.paymentgateway.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.labs64.paymentgateway.exception.ConflictException;
import io.labs64.paymentgateway.exception.IdempotencyConflictException;
import io.labs64.paymentgateway.idempotency.IdempotencyContext;
import io.labs64.paymentgateway.idempotency.IdempotencyResponse;
import io.labs64.paymentgateway.message.IdempotencyMessages;
import io.labs64.paymentgateway.repository.RedisIdempotencyRepository;
import io.labs64.paymentgateway.repository.RedisIdempotencyRepository.CompletionStatus;
import io.labs64.paymentgateway.repository.RedisIdempotencyRepository.StartResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class IdempotencyServiceImpl implements IdempotencyService {

    private static final TypeReference<Map<String, List<String>>> HEADERS_TYPE = new TypeReference<>() { };

    private final RedisIdempotencyRepository repository;
    private final ObjectMapper objectMapper;
    private final IdempotencyMessages msg;

    @Override
    public Optional<IdempotencyResponse> startOrReplay(final IdempotencyContext context) {
        final StartResult result = repository.startOrReplay(context);

        return switch (result.status()) {
            case STARTED -> Optional.empty();
            case COMPLETED -> Optional.of(toResponse(result));
            case CONFLICT -> throw new IdempotencyConflictException(msg.requestHashConflict());
            case PROCESSING -> throw new ConflictException(msg.stillProcessing());
        };
    }

    @Override
    public void complete(final IdempotencyContext context, final IdempotencyResponse response) {
        final CompletionStatus result = repository.complete(
                context,
                response.status(),
                writeJson(toSerializableHeaders(response.headers())),
                writeJson(response.body()));

        switch (result) {
            case CONFLICT -> throw new IdempotencyConflictException(msg.requestHashConflict());
            case MISSING -> throw new ConflictException(msg.recordNotInitialized());
            case STALE_OWNER -> throw new ConflictException(msg.stillProcessing());
            case COMPLETED -> log.debug(
                    "Stored idempotency response in Redis | operation={}, tenantId={}, status={}",
                    context.operation().key(), context.tenantId(), response.status());
        }
    }

    private IdempotencyResponse toResponse(final StartResult result) {
        try {
            final HttpHeaders headers = new HttpHeaders();
            final Map<String, List<String>> storedHeaders = objectMapper.readValue(
                    result.responseHeaders(), HEADERS_TYPE);
            storedHeaders.forEach(headers::put);
            final Object body = objectMapper.readValue(result.responseBody(), Object.class);
            return new IdempotencyResponse(Integer.parseInt(result.responseStatus()), headers, body);
        } catch (JsonProcessingException | NumberFormatException exception) {
            throw new IllegalStateException("Cannot deserialize the idempotency response from Redis.", exception);
        }
    }

    private Map<String, List<String>> toSerializableHeaders(final HttpHeaders headers) {
        final Map<String, List<String>> result = new LinkedHashMap<>();
        headers.forEach((name, values) -> result.put(name, List.copyOf(values)));
        return result;
    }

    private String writeJson(final Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Cannot serialize the idempotency response for Redis.", exception);
        }
    }
}
