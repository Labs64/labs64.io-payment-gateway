package io.labs64.paymentgateway.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import io.labs64.paymentgateway.entity.TransactionEntity;
import io.labs64.paymentgateway.psp.PspNextAction;
import io.labs64.paymentgateway.v1.model.ExecutePaymentResponse;
import io.labs64.paymentgateway.v1.model.NextAction;
import io.labs64.paymentgateway.v1.model.Transaction;
import io.labs64.paymentgateway.v1.model.TransactionResponse;
import io.labs64.paymentgateway.v1.model.TransactionStatus;

/**
 * MapStruct mapper for converting between {@link TransactionEntity} and API DTOs.
 */
@Mapper(componentModel = "spring")
public interface TransactionMapper {

    @Mapping(source = "payment.id", target = "paymentId")
    Transaction toDto(TransactionEntity entity);

    default TransactionStatus map(TransactionEntity.TransactionStatus status) {
        if (status == null) {
            return null;
        }
        return TransactionStatus.fromValue(status.name());
    }

    default TransactionResponse toTransactionResponse(TransactionEntity entity) {
        final TransactionResponse response = new TransactionResponse();
        response.setTransaction(toDto(entity));
        return response;
    }

    default ExecutePaymentResponse toExecuteResponse(TransactionEntity entity, PspNextAction pspNextAction) {
        final ExecutePaymentResponse response = new ExecutePaymentResponse();
        response.setTransaction(toDto(entity));
        if (pspNextAction != null) {
            response.setNextAction(mapNextAction(pspNextAction));
        }
        return response;
    }

    default NextAction mapNextAction(PspNextAction pspNextAction) {
        if (pspNextAction == null) {
            return null;
        }
        final NextAction nextAction = new NextAction();
        if (pspNextAction.getType() != null) {
            nextAction.setType(NextAction.TypeEnum.fromValue(pspNextAction.getType()));
        }
        nextAction.setDetails(pspNextAction.getDetails());
        return nextAction;
    }
}
