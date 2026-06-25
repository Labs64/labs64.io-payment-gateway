package io.labs64.paymentgateway.mapper;

import io.labs64.paymentgateway.entity.PaymentTransactionEntity;
import io.labs64.paymentgateway.model.PaymentTransaction;
import io.labs64.paymentgateway.model.PaymentTransactionsResponse;
import java.util.UUID;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.springframework.data.domain.Page;

/**
 * Maps payment transaction entities and generated API DTOs.
 */
@Mapper(config = MapperConfigBase.class)
public interface PaymentTransactionMapper {

    @Mapping(target = "paymentId", expression = "java(resolvePaymentId(entity))")
    PaymentTransaction toDto(PaymentTransactionEntity entity);

    default UUID resolvePaymentId(final PaymentTransactionEntity entity) {
        if (entity == null) {
            return null;
        }
        if (entity.getPaymentId() != null) {
            return entity.getPaymentId();
        }
        return entity.getPayment() != null ? entity.getPayment().getId() : null;
    }

    default PaymentTransactionsResponse toPaymentTransactionsResponse(final Page<PaymentTransactionEntity> entities) {
        final PaymentTransactionsResponse response = toPaymentTransactionsResponseFromIterable(entities.getContent());
        response.setPage(entities.getNumber());
        response.setPageSize(entities.getSize());
        response.setTotalItems(entities.getTotalElements());
        response.setTotalPages(entities.getTotalPages());
        response.setHasPrev(entities.hasPrevious());
        response.setHasNext(entities.hasNext());
        return response;
    }

    default PaymentTransactionsResponse toPaymentTransactionsResponseFromIterable(final Iterable<PaymentTransactionEntity> entities) {
        final PaymentTransactionsResponse response = new PaymentTransactionsResponse();
        response.setItems(java.util.stream.StreamSupport.stream(entities.spliterator(), false)
                .map(this::toDto)
                .toList());
        return response;
    }
}
