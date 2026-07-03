package io.labs64.paymentgateway.service;

import java.util.UUID;

import io.labs64.paymentgateway.correlation.CorrelationContextHolder;
import io.labs64.paymentgateway.correlation.CorrelationEntityType;
import io.labs64.paymentgateway.entity.CorrelationTraceEntity;
import io.labs64.paymentgateway.repository.CorrelationTraceRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CorrelationTraceServiceImplTest {

    @Mock
    private CorrelationTraceRepository repository;

    @InjectMocks
    private CorrelationTraceServiceImpl service;

    @AfterEach
    void tearDown() {
        CorrelationContextHolder.clear();
    }

    @Test
    void createReturnsSavedCorrelationTrace() {
        final CorrelationTraceEntity entity = trace("correlation-1");
        final CorrelationTraceEntity saved = trace("correlation-1");
        saved.setId(UUID.randomUUID());
        when(repository.save(entity)).thenReturn(saved);

        assertThat(service.create(entity)).isSameAs(saved);
    }

    @Test
    void createBuildsCorrelationTraceFromValues() {
        final UUID entityId = UUID.randomUUID();
        when(repository.save(org.mockito.ArgumentMatchers.any(CorrelationTraceEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        final CorrelationTraceEntity result = service.create(
                CorrelationEntityType.PAYMENT_TRANSACTION,
                entityId,
                "correlation-1");

        assertThat(result.getEntityType()).isEqualTo(CorrelationEntityType.PAYMENT_TRANSACTION);
        assertThat(result.getEntityId()).isEqualTo(entityId);
        assertThat(result.getCorrelationId()).isEqualTo("correlation-1");
    }

    @Test
    void attachUsesCurrentCorrelationContext() {
        final UUID entityId = UUID.randomUUID();
        CorrelationContextHolder.set("correlation-1");
        when(repository.save(org.mockito.ArgumentMatchers.any(CorrelationTraceEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        final CorrelationTraceEntity result = service.attach(CorrelationEntityType.PAYMENT, entityId);

        assertThat(result.getCorrelationId()).isEqualTo("correlation-1");
        assertThat(result.getEntityType()).isEqualTo(CorrelationEntityType.PAYMENT);
        verify(repository).save(org.mockito.ArgumentMatchers.any(CorrelationTraceEntity.class));
    }

    private static CorrelationTraceEntity trace(final String correlationId) {
        return CorrelationTraceEntity.builder()
                .entityType(CorrelationEntityType.PAYMENT)
                .entityId(UUID.randomUUID())
                .correlationId(correlationId)
                .build();
    }
}
