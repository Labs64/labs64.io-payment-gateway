package io.labs64.paymentgateway.mapper;

import java.util.LinkedHashMap;

import org.mapstruct.AfterMapping;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;
import org.springframework.data.domain.Page;

import io.labs64.paymentgateway.entity.PaymentProviderEntity;
import io.labs64.paymentgateway.model.PaymentProvider;
import io.labs64.paymentgateway.model.PaymentProviderCreateRequest;
import io.labs64.paymentgateway.model.PaymentProviderListResponse;
import io.labs64.paymentgateway.model.PaymentProviderUpdateRequest;

@Mapper(config = MapperConfigBase.class)
public interface PaymentProviderMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "tenantId", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    PaymentProviderEntity toEntity(PaymentProviderCreateRequest source);

    @AfterMapping
    default void defaultConfig(@MappingTarget final PaymentProviderEntity target) {
        if (target.getConfig() == null) {
            target.setConfig(new LinkedHashMap<>());
        }
    }

    @Mapping(target = "icon", ignore = true)
    @Mapping(target = "recurring", ignore = true)
    @Mapping(target = "config", ignore = true)
    PaymentProvider toDto(PaymentProviderEntity entity);

    default PaymentProvider toDtoWithConfig(final PaymentProviderEntity entity) {
        final PaymentProvider dto = toDto(entity);
        if (dto != null) {
            dto.setConfig(entity.getConfig());
        }
        return dto;
    }

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "tenantId", ignore = true)
    @Mapping(target = "provider", ignore = true)
    @Mapping(target = "config", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    void updateEntity(PaymentProviderUpdateRequest source, @MappingTarget PaymentProviderEntity target);

    @AfterMapping
    default void mergeConfig(final PaymentProviderUpdateRequest source, @MappingTarget final PaymentProviderEntity target) {
        if (source.getConfig() != null) {
            if (target.getConfig() == null) {
                target.setConfig(new LinkedHashMap<>());
            }
            target.getConfig().putAll(source.getConfig());
        }
    }

    default PaymentProviderListResponse toPage(final Page<PaymentProviderEntity> source) {
        final PaymentProviderListResponse response = new PaymentProviderListResponse();
        response.setItems(source.getContent().stream()
                .map(this::toDto)
                .toList());
        return response;
    }
}
