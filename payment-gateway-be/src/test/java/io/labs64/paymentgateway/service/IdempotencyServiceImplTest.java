package io.labs64.paymentgateway.service;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Optional;

import io.labs64.paymentgateway.config.PaymentGatewayProperties;
import io.labs64.paymentgateway.domain.IdempotencyRequestStatus;
import io.labs64.paymentgateway.entity.IdempotencyRequestEntity;
import io.labs64.paymentgateway.exception.ConflictException;
import io.labs64.paymentgateway.exception.IdempotencyConflictException;
import io.labs64.paymentgateway.idempotency.IdempotencyContext;
import io.labs64.paymentgateway.idempotency.IdempotencyOperation;
import io.labs64.paymentgateway.idempotency.IdempotencyResponse;
import io.labs64.paymentgateway.message.IdempotencyMessages;
import io.labs64.paymentgateway.repository.IdempotencyRequestRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IdempotencyServiceImplTest {

    private static final String TENANT_ID = "tenant-a";
    private static final String KEY = "idk-1";
    private static final String HASH = "hash-1";
    private static final String OPERATION = "POST:/api/v1/payments/{paymentId}/pay";

    @Mock
    private IdempotencyRequestRepository repository;

    @Mock
    private IdempotencyMessages msg;

    private PaymentGatewayProperties properties;

    @InjectMocks
    private IdempotencyServiceImpl service;

    @BeforeEach
    void setUp() {
        properties = new PaymentGatewayProperties();
        properties.getIdempotency().setDatabaseTtl(Duration.ofHours(1));
        properties.getIdempotency().setProcessingTimeout(Duration.ofMinutes(5));
        properties.getIdempotency().setCleanupBatchSize(25);
        service = new IdempotencyServiceImpl(repository, properties, msg);
    }

    @Test
    void startOrReplayCreatesProcessingRecordWhenNoRecordExists() {
        when(repository.findByTenantIdAndOperationAndKey(TENANT_ID, OPERATION, KEY)).thenReturn(Optional.empty());
        when(repository.insertProcessingIfAbsent(
                eq(TENANT_ID),
                eq(OPERATION),
                eq(KEY),
                eq(HASH),
                eq(IdempotencyRequestStatus.PROCESSING.name()),
                any(OffsetDateTime.class))).thenReturn(1);

        assertThat(service.startOrReplay(context())).isEmpty();

        verify(repository).insertProcessingIfAbsent(
                eq(TENANT_ID),
                eq(OPERATION),
                eq(KEY),
                eq(HASH),
                eq(IdempotencyRequestStatus.PROCESSING.name()),
                any(OffsetDateTime.class));
    }

    @Test
    void startOrReplayReturnsCompletedCachedResponse() {
        final IdempotencyRequestEntity record = record(IdempotencyRequestStatus.COMPLETED);
        record.setResponseStatus(201);
        record.setResponseHeaders(Map.of("Location", java.util.List.of("/payments/123")));
        record.setResponseBody(Map.of("id", "payment-1"));
        when(repository.findByTenantIdAndOperationAndKey(TENANT_ID, OPERATION, KEY)).thenReturn(Optional.of(record));

        final Optional<IdempotencyResponse> result = service.startOrReplay(context());

        assertThat(result).isPresent();
        assertThat(result.get().status()).isEqualTo(201);
        assertThat(result.get().headers().getFirst("Location")).isEqualTo("/payments/123");
        assertThat(result.get().body()).isEqualTo(Map.of("id", "payment-1"));
    }

    @Test
    void startOrReplayThrowsWhenRequestHashDiffers() {
        final IdempotencyRequestEntity record = record(IdempotencyRequestStatus.COMPLETED);
        record.setRequestHash("different-hash");
        when(repository.findByTenantIdAndOperationAndKey(TENANT_ID, OPERATION, KEY)).thenReturn(Optional.of(record));
        when(msg.requestHashConflict()).thenReturn("hash conflict");

        assertThatThrownBy(() -> service.startOrReplay(context()))
                .isInstanceOf(IdempotencyConflictException.class);
    }

    @Test
    void startOrReplayThrowsWhenSameRequestIsStillProcessing() {
        final IdempotencyRequestEntity record = record(IdempotencyRequestStatus.PROCESSING);
        record.setUpdatedAt(OffsetDateTime.now());
        when(repository.findByTenantIdAndOperationAndKey(TENANT_ID, OPERATION, KEY)).thenReturn(Optional.of(record));
        when(msg.stillProcessing()).thenReturn("still processing");

        assertThatThrownBy(() -> service.startOrReplay(context()))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void startOrReplayResetsExpiredRecord() {
        final IdempotencyRequestEntity record = record(IdempotencyRequestStatus.COMPLETED);
        record.setExpiresAt(OffsetDateTime.now().minusMinutes(1));
        record.setResponseStatus(200);
        record.setResponseHeaders(Map.of("X-Test", java.util.List.of("value")));
        record.setResponseBody(Map.of("old", true));
        when(repository.findByTenantIdAndOperationAndKey(TENANT_ID, OPERATION, KEY)).thenReturn(Optional.of(record));

        assertThat(service.startOrReplay(context())).isEmpty();

        assertThat(record.getStatus()).isEqualTo(IdempotencyRequestStatus.PROCESSING);
        assertThat(record.getResponseStatus()).isNull();
        assertThat(record.getResponseHeaders()).isNull();
        assertThat(record.getResponseBody()).isNull();
        verify(repository).save(record);
    }

    @Test
    void startOrReplayResetsTimedOutProcessingRecord() {
        final IdempotencyRequestEntity record = record(IdempotencyRequestStatus.PROCESSING);
        record.setUpdatedAt(OffsetDateTime.now().minusMinutes(10));
        when(repository.findByTenantIdAndOperationAndKey(TENANT_ID, OPERATION, KEY)).thenReturn(Optional.of(record));

        assertThat(service.startOrReplay(context())).isEmpty();

        assertThat(record.getStatus()).isEqualTo(IdempotencyRequestStatus.PROCESSING);
        verify(repository).save(record);
    }

    @Test
    void completeStoresResponseOnInitializedRecord() {
        final IdempotencyRequestEntity record = record(IdempotencyRequestStatus.PROCESSING);
        final HttpHeaders headers = new HttpHeaders();
        headers.add("Location", "/payments/123");
        when(repository.findByTenantIdAndOperationAndKey(TENANT_ID, OPERATION, KEY)).thenReturn(Optional.of(record));

        service.complete(context(), new IdempotencyResponse(201, headers, Map.of("id", "payment-1")));

        assertThat(record.getStatus()).isEqualTo(IdempotencyRequestStatus.COMPLETED);
        assertThat(record.getResponseStatus()).isEqualTo(201);
        assertThat(record.getResponseHeaders()).containsKey("Location");
        assertThat(record.getResponseBody()).isEqualTo(Map.of("id", "payment-1"));
        verify(repository).save(record);
    }

    @Test
    void completeThrowsWhenRecordWasNotInitialized() {
        when(repository.findByTenantIdAndOperationAndKey(TENANT_ID, OPERATION, KEY)).thenReturn(Optional.empty());
        when(msg.recordNotInitialized()).thenReturn("not initialized");

        assertThatThrownBy(() -> service.complete(context(), new IdempotencyResponse(200, new HttpHeaders(), null)))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void completeThrowsWhenRequestHashDiffers() {
        final IdempotencyRequestEntity record = record(IdempotencyRequestStatus.PROCESSING);
        record.setRequestHash("different-hash");
        when(repository.findByTenantIdAndOperationAndKey(TENANT_ID, OPERATION, KEY)).thenReturn(Optional.of(record));
        when(msg.requestHashConflict()).thenReturn("hash conflict");

        assertThatThrownBy(() -> service.complete(context(), new IdempotencyResponse(200, new HttpHeaders(), null)))
                .isInstanceOf(IdempotencyConflictException.class);
    }

    @Test
    void cleanupExpiredDeletesConfiguredBatchSize() {
        when(repository.deleteExpired(any(OffsetDateTime.class), eq(25))).thenReturn(3);

        service.cleanupExpired();

        verify(repository).deleteExpired(any(OffsetDateTime.class), eq(25));
    }

    private static IdempotencyContext context() {
        return new IdempotencyContext(
                TENANT_ID,
                KEY,
                HASH,
                new IdempotencyOperation("POST", "/api/v1/payments/{paymentId}/pay"));
    }

    private static IdempotencyRequestEntity record(final IdempotencyRequestStatus status) {
        return IdempotencyRequestEntity.builder()
                .tenantId(TENANT_ID)
                .operation(OPERATION)
                .key(KEY)
                .requestHash(HASH)
                .status(status)
                .expiresAt(OffsetDateTime.now().plusMinutes(30))
                .updatedAt(OffsetDateTime.now())
                .build();
    }
}
