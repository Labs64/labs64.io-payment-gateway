package io.labs64.paymentgateway.mapper;

import java.util.Map;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import io.labs64.paymentgateway.entity.PaymentEntity;
import io.labs64.paymentgateway.psp.PspNextAction;
import io.labs64.paymentgateway.v1.model.CreatePaymentResponse;
import io.labs64.paymentgateway.v1.model.NextAction;
import io.labs64.paymentgateway.v1.model.Payment;
import io.labs64.paymentgateway.v1.model.PaymentDetailResponse;
import io.labs64.paymentgateway.v1.model.PaymentStatus;
import io.labs64.paymentgateway.v1.model.PaymentType;

/**
 * MapStruct mapper for converting between {@link PaymentEntity} and API DTOs.
 */
@Mapper(componentModel = "spring")
public interface PaymentMapper {

    @Mapping(source = "purchaseOrderRef", target = "purchaseOrder.referenceId")
    Payment toDto(PaymentEntity entity);

    // Allows mapping JSONB-backed Map<String, Object> fields into String DTO fields.
    default String map(Object value) {
        return value == null ? null : value.toString();
    }

    default PaymentStatus map(PaymentEntity.PaymentStatus status) {
        if (status == null) {
            return null;
        }
        return PaymentStatus.fromValue(status.name());
    }

    default PaymentType map(PaymentEntity.PaymentType type) {
        if (type == null) {
            return null;
        }
        return PaymentType.fromValue(type.name());
    }

    default CreatePaymentResponse toCreateResponse(PaymentEntity entity, PspNextAction pspNextAction) {
        final CreatePaymentResponse response = new CreatePaymentResponse();
        response.setPayment(toDto(entity));
        if (pspNextAction != null) {
            response.setNextAction(mapNextAction(pspNextAction));
        }
        return response;
    }

    default PaymentDetailResponse toDetailResponse(PaymentEntity entity) {
        final PaymentDetailResponse response = new PaymentDetailResponse();
        response.setPayment(toDto(entity));
        if (entity.getNextAction() != null && !entity.getNextAction().isEmpty()) {
            final NextAction nextAction = new NextAction();
            final Object type = entity.getNextAction().get("type");
            if (type != null) {
                nextAction.setType(NextAction.TypeEnum.fromValue(type.toString()));
            }
            @SuppressWarnings("unchecked")
            final Map<String, Object> details = (Map<String, Object>) entity.getNextAction().get("details");
            nextAction.setDetails(details);
            response.setNextAction(nextAction);
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
