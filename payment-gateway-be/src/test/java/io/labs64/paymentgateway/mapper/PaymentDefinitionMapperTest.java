package io.labs64.paymentgateway.mapper;

import io.labs64.paymentgateway.config.PaymentGatewayProperties;
import io.labs64.paymentgateway.model.PaymentDefinition;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentDefinitionMapperTest {

    private final PaymentDefinitionMapper mapper = Mappers.getMapper(PaymentDefinitionMapper.class);

    @Test
    void toDtoKeepsOpenApiSchemaDefault() {
        final PaymentGatewayProperties.PaymentDefinition source = new PaymentGatewayProperties.PaymentDefinition();
        source.setProvider("stripe");
        source.setName("Stripe");
        source.setDescription("Cards");

        final PaymentDefinition dto = mapper.toDto(source);

        assertThat(dto.get$Schema().toString())
                .isEqualTo("https://labs64.io/schemas/payment-gateway/PaymentDefinition/1.0.0.json");
    }
}
