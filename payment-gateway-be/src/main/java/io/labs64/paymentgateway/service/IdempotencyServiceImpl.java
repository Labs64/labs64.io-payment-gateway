package io.labs64.paymentgateway.service;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import io.labs64.paymentgateway.idempotency.IdempotencyContext;
import io.labs64.paymentgateway.idempotency.IdempotencyResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.labs64.paymentgateway.config.PaymentGatewayProperties;
import io.labs64.paymentgateway.domain.IdempotencyRequestStatus;
import io.labs64.paymentgateway.entity.IdempotencyRequestEntity;
import io.labs64.paymentgateway.exception.ConflictException;
import io.labs64.paymentgateway.exception.IdempotencyConflictException;
import io.labs64.paymentgateway.message.IdempotencyMessages;
import io.labs64.paymentgateway.repository.IdempotencyRequestRepository;
import lombok.RequiredArgsConstructor;

@Slf4j
@Service
@RequiredArgsConstructor
public class IdempotencyServiceImpl implements IdempotencyService {
    private final IdempotencyRequestRepository repository;
    private final PaymentGatewayProperties properties;
    private final IdempotencyMessages msg;

    @Override
    @Transactional
    public Optional<IdempotencyResponse> startOrReplay(final IdempotencyContext context) {
        final OffsetDateTime now = OffsetDateTime.now();
        final String operation = context.operation().key();

        return repository.findByTenantIdAndOperationAndKey(context.tenantId(), operation, context.idempotencyKey())
                .map(record -> handleExisting(record, context, now))
                .orElseGet(() -> createProcessingRecord(context, now));
    }

    @Override
    @Transactional
    public void complete(final IdempotencyContext context, final IdempotencyResponse response) {
        final String operation = context.operation().key();
        final IdempotencyRequestEntity record = repository
                .findByTenantIdAndOperationAndKey(context.tenantId(), operation, context.idempotencyKey())
                .orElseThrow(() -> new ConflictException(msg.recordNotInitialized()));

        if (!record.getRequestHash().equals(context.requestHash())) {
            throw new IdempotencyConflictException(msg.requestHashConflict());
        }

        record.setStatus(IdempotencyRequestStatus.COMPLETED);
        record.setResponseStatus(response.status());
        record.setResponseHeaders(toResponseHeaders(response.headers()));
        record.setResponseBody(response.body());
        record.setExpiresAt(OffsetDateTime.now().plus(properties.getIdempotency().getDatabaseTtl()));
        repository.save(record);

        log.debug("Stored idempotency response | operation={}, tenantId={}, status={}",
                operation, context.tenantId(), response.status());
    }

    @Override
    @Transactional
    public void cleanupExpired() {
        final int deleted = repository.deleteExpired(
                OffsetDateTime.now(),
                properties.getIdempotency().getCleanupBatchSize());
        if (deleted > 0) {
            log.info("Deleted expired idempotency records | count={}", deleted);
        }
    }

    private Optional<IdempotencyResponse> handleExisting(
            final IdempotencyRequestEntity record,
            final IdempotencyContext context,
            final OffsetDateTime now) {
        if (!record.getRequestHash().equals(context.requestHash())) {
            throw new IdempotencyConflictException(msg.requestHashConflict());
        }

        if (record.getExpiresAt().isBefore(now)) {
            resetProcessingRecord(record, context, now);
            return Optional.empty();
        }

        if (record.getStatus() == IdempotencyRequestStatus.COMPLETED) {
            return Optional.of(new IdempotencyResponse(
                    record.getResponseStatus(),
                    toHttpHeaders(record.getResponseHeaders()),
                    record.getResponseBody()));
        }

        if (record.getUpdatedAt() != null
                && record.getUpdatedAt().plus(properties.getIdempotency().getProcessingTimeout()).isBefore(now)) {
            resetProcessingRecord(record, context, now);
            return Optional.empty();
        }

        throw new ConflictException(msg.stillProcessing());
    }

    private Optional<IdempotencyResponse> createProcessingRecord(final IdempotencyContext context, final OffsetDateTime now) {
        final int inserted = repository.insertProcessingIfAbsent(
                context.tenantId(),
                context.operation().key(),
                context.idempotencyKey(),
                context.requestHash(),
                IdempotencyRequestStatus.PROCESSING.name(),
                now.plus(properties.getIdempotency().getDatabaseTtl()));

        if (inserted == 0) {
            return repository
                    .findByTenantIdAndOperationAndKey(context.tenantId(), context.operation().key(), context.idempotencyKey())
                    .map(record -> handleExisting(record, context, now))
                    .orElseThrow(() -> new ConflictException(msg.recordNotInitialized()));
        }

        log.debug("Created idempotency processing record | operation={}, tenantId={}",
                context.operation().key(), context.tenantId());
        return Optional.empty();
    }

    private void resetProcessingRecord(
            final IdempotencyRequestEntity record,
            final IdempotencyContext context,
            final OffsetDateTime now) {
        record.setRequestHash(context.requestHash());
        record.setStatus(IdempotencyRequestStatus.PROCESSING);
        record.setResponseStatus(null);
        record.setResponseHeaders(null);
        record.setResponseBody(null);
        record.setExpiresAt(now.plus(properties.getIdempotency().getDatabaseTtl()));
        repository.save(record);
    }

    private Map<String, Object> toResponseHeaders(final HttpHeaders headers) {
        final Map<String, Object> result = new LinkedHashMap<>();
        headers.forEach((name, values) -> result.put(name, List.copyOf(values)));
        return result;
    }

    private HttpHeaders toHttpHeaders(final Map<String, Object> source) {
        final HttpHeaders headers = new HttpHeaders();
        if (source == null) {
            return headers;
        }
        source.forEach((name, value) -> {
            if (value instanceof List<?> values) {
                values.forEach(item -> headers.add(name, String.valueOf(item)));
            } else if (value != null) {
                headers.add(name, String.valueOf(value));
            }
        });
        return headers;
    }
}
