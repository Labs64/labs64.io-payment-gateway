package io.labs64.paymentgateway.mapper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.labs64.paymentgateway.entity.PaymentProviderEntity;
import io.labs64.paymentgateway.model.PaymentProvider;
import io.labs64.paymentgateway.model.PaymentProviderCreateRequest;
import io.labs64.paymentgateway.model.PaymentProviderListResponse;
import io.labs64.paymentgateway.model.PaymentProviderUpdateRequest;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.springframework.data.domain.PageImpl;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentProviderMapperTest {

    private final PaymentProviderMapper mapper = Mappers.getMapper(PaymentProviderMapper.class);

    @Test
    void toEntityDoesNotTakeProviderOwnershipFromCreateRequest() {
        final PaymentProviderCreateRequest request = new PaymentProviderCreateRequest(true);
        request.setConfig(Map.of("apiKey", "secret"));
        request.setName("Tenant Stripe");
        request.setDescription("Cards");

        final PaymentProviderEntity entity = mapper.toEntity(request);

        assertThat(entity.getTenantId()).isNull();
        assertThat(entity.getProvider()).isNull();
        assertThat(entity.isActive()).isTrue();
        assertThat(entity.getName()).isEqualTo("Tenant Stripe");
        assertThat(entity.getDescription()).isEqualTo("Cards");
        assertThat(entity.getConfig()).containsEntry("apiKey", "secret");
    }

    @Test
    void toDtoUsesProviderAsPublicIdAndMasksConfigByDefault() {
        final PaymentProviderEntity entity = entity("tenant-a", "stripe");

        final PaymentProvider dto = mapper.toDto(entity, Set.of());

        assertThat(dto.getId()).isEqualTo("stripe");
        assertThat(dto.getName()).isEqualTo("Stripe");
        assertThat(dto.getDescription()).isEqualTo("Cards");
        assertThat(dto.getActive()).isTrue();
        assertThat(dto.getConfig()).isNull();
    }

    @Test
    void toDtoIncludesConfigOnlyWhenRequested() {
        final PaymentProviderEntity entity = entity("tenant-a", "stripe");

        final PaymentProvider dto = mapper.toDto(entity, Set.of("config"));

        assertThat(dto.getConfig()).containsEntry("apiKey", "secret");
    }

    @Test
    void updateEntityDoesNotChangeTenantOrProviderAndMergesConfig() {
        final PaymentProviderEntity entity = entity("tenant-a", "stripe");
        final PaymentProviderUpdateRequest request = new PaymentProviderUpdateRequest()
                .active(false)
                .name("Updated Stripe")
                .config(Map.of("webhookSecret", "new-secret"));

        mapper.updateEntity(request, entity);

        assertThat(entity.getTenantId()).isEqualTo("tenant-a");
        assertThat(entity.getProvider()).isEqualTo("stripe");
        assertThat(entity.isActive()).isFalse();
        assertThat(entity.getName()).isEqualTo("Updated Stripe");
        assertThat(entity.getDescription()).isEqualTo("Cards");
        assertThat(entity.getConfig())
                .containsEntry("apiKey", "secret")
                .containsEntry("webhookSecret", "new-secret");
    }

    @Test
    void updateEntityCreatesConfigMapWhenTargetConfigIsNull() {
        final PaymentProviderEntity entity = entity("tenant-a", "stripe");
        entity.setConfig(null);
        final PaymentProviderUpdateRequest request = new PaymentProviderUpdateRequest()
                .config(Map.of("apiKey", "secret"));

        mapper.updateEntity(request, entity);

        assertThat(entity.getConfig()).containsEntry("apiKey", "secret");
    }

    @Test
    void toPageMapsItemsWithConfigFlag() {
        final PaymentProviderListResponse response = mapper.toPage(
                new PageImpl<>(List.of(entity("tenant-a", "stripe"))),
                Set.of("config"));

        assertThat(response.getItems()).hasSize(1);
        assertThat(response.getItems().get(0).getId()).isEqualTo("stripe");
        assertThat(response.getItems().get(0).getConfig()).containsEntry("apiKey", "secret");
    }

    private static PaymentProviderEntity entity(final String tenantId, final String provider) {
        return PaymentProviderEntity.builder()
                .tenantId(tenantId)
                .provider(provider)
                .active(true)
                .name("Stripe")
                .description("Cards")
                .config(new LinkedHashMap<>(Map.of("apiKey", "secret")))
                .build();
    }
}
