package io.labs64.paymentgateway.service;

import io.labs64.paymentgateway.correlation.CorrelationEntityType;
import io.labs64.paymentgateway.entity.CorrelationTraceEntity;

import java.util.UUID;

public interface CorrelationTraceService {
    CorrelationTraceEntity create(CorrelationTraceEntity entity);
    CorrelationTraceEntity create(CorrelationEntityType entityType, UUID entityId, String correlationId);
    CorrelationTraceEntity attach(CorrelationEntityType entityType, UUID entityId);
}
