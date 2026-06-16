package io.labs64.paymentgateway.service;

import io.labs64.paymentgateway.correlation.CorrelationContextHolder;
import io.labs64.paymentgateway.correlation.CorrelationEntityType;
import io.labs64.paymentgateway.entity.CorrelationTraceEntity;
import io.labs64.paymentgateway.repository.CorrelationTraceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class CorrelationTraceServiceImpl implements CorrelationTraceService {
    private final CorrelationTraceRepository repository;

    @Override
    public CorrelationTraceEntity create(final CorrelationTraceEntity entity) {

        final CorrelationTraceEntity saved = repository.save(entity);
        log.debug("Creating correlation for entity={}, entityId={}, correlationId={}", saved.getEntityType(), saved.getEntityId(), saved.getCorrelationId());

        return saved;
    }

    @Override
    public CorrelationTraceEntity create(final CorrelationEntityType entityType, final UUID entityId, final String correlationId) {
        return create(CorrelationTraceEntity.builder()
                .correlationId(correlationId)
                .entityType(entityType)
                .entityId(entityId)
                .build());
    }

    @Override
    public CorrelationTraceEntity attach(final CorrelationEntityType entityType, final UUID entityId) {
        return create(entityType, entityId, CorrelationContextHolder.require());
    }
}
