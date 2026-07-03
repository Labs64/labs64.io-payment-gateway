package io.labs64.paymentgateway.controller;

import java.util.List;

import io.labs64.paymentgateway.config.PaymentGatewayProperties.PaymentDefinition;
import io.labs64.paymentgateway.mapper.PaymentDefinitionMapper;
import io.labs64.paymentgateway.model.PaymentDefinitionListResponse;
import io.labs64.paymentgateway.service.PaymentDefinitionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentDefinitionControllerTest {

    @Mock
    private PaymentDefinitionService service;

    @Mock
    private PaymentDefinitionMapper mapper;

    @InjectMocks
    private PaymentDefinitionController controller;

    @Test
    void listPaymentDefinitionsReturnsEnabledDefinitions() {
        final List<PaymentDefinition> enabledDefinitions = List.of(definition("stripe"), definition("noop"));
        final PaymentDefinitionListResponse response = new PaymentDefinitionListResponse();

        when(service.listEnabled()).thenReturn(enabledDefinitions);
        when(mapper.toListResponse(enabledDefinitions)).thenReturn(response);

        final ResponseEntity<PaymentDefinitionListResponse> result = controller.listPaymentDefinitions();

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isSameAs(response);

        verify(service).listEnabled();
        verify(mapper).toListResponse(enabledDefinitions);
        verifyNoMoreInteractions(service, mapper);
    }

    private static PaymentDefinition definition(final String provider) {
        final PaymentDefinition definition = new PaymentDefinition();
        definition.setProvider(provider);
        definition.setEnabled(true);
        return definition;
    }
}
