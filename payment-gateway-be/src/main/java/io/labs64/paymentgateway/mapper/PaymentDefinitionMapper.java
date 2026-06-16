package io.labs64.paymentgateway.mapper;

import java.util.List;

import io.labs64.paymentgateway.config.PaymentGatewayProperties;
import io.labs64.paymentgateway.model.PaymentDefinition;
import io.labs64.paymentgateway.model.PaymentDefinitionListResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(config = MapperConfigBase.class)
public interface PaymentDefinitionMapper {

    @Mapping(target = "icon", ignore = true)
    PaymentDefinition toDto(PaymentGatewayProperties.PaymentDefinition source);

    default PaymentDefinitionListResponse toListResponse(final List<PaymentGatewayProperties.PaymentDefinition> source) {
        final PaymentDefinitionListResponse response = new PaymentDefinitionListResponse();
        response.setItems(source.stream()
                .map(this::toDto)
                .toList());
        return response;
    }
}
