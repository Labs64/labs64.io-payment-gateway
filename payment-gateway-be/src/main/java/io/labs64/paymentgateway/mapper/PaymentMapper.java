package io.labs64.paymentgateway.mapper;

import io.labs64.paymentgateway.entity.PaymentEntity;
import io.labs64.paymentgateway.model.CreatePaymentRequest;
import io.labs64.paymentgateway.model.Payment;
import io.labs64.paymentgateway.model.PaymentListResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.springframework.data.domain.Page;

/**
 * Maps payment entities and generated API DTOs.
 */
@Mapper(config = MapperConfigBase.class, uses = PaymentJsonMapper.class)
public interface PaymentMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "tenantId", ignore = true)
    @Mapping(target = "paymentProviderId", ignore = true)
    @Mapping(target = "paymentProvider", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "description", ignore = true)
    @Mapping(target = "purchaseOrder", source = "purchaseOrder", qualifiedByName = "purchaseOrderToMap")
    @Mapping(target = "billingInfo", source = "billingInfo", qualifiedByName = "billingInfoToMap")
    @Mapping(target = "shippingInfo", source = "shippingInfo", qualifiedByName = "shippingInfoToMap")
    @Mapping(target = "recurrence", source = "recurrence", qualifiedByName = "recurrenceToMap")
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    PaymentEntity toEntity(CreatePaymentRequest source);

    @Mapping(target = "provider", source = "paymentProvider.provider")
    @Mapping(target = "purchaseOrder", source = "purchaseOrder", qualifiedByName = "mapToPurchaseOrder")
    @Mapping(target = "billingInfo", source = "billingInfo", qualifiedByName = "mapToBillingInfo")
    @Mapping(target = "shippingInfo", source = "shippingInfo", qualifiedByName = "mapToShippingInfo")
    @Mapping(target = "recurrence", source = "recurrence", qualifiedByName = "mapToRecurrence")
    @Mapping(target = "lastPaymentAt", ignore = true)
    @Mapping(target = "nextPaymentAt", ignore = true)
    Payment toDto(PaymentEntity entity);

    default PaymentListResponse toPage(final Page<PaymentEntity> source) {
        final PaymentListResponse response = new PaymentListResponse();
        response.setItems(source.getContent().stream()
                .map(this::toDto)
                .toList());
        response.setPage(source.getNumber());
        response.setPageSize(source.getSize());
        response.setTotalItems(source.getTotalElements());
        response.setTotalPages(source.getTotalPages());
        response.setHasPrev(source.hasPrevious());
        response.setHasNext(source.hasNext());
        return response;
    }
}
