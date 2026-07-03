package io.labs64.paymentgateway.controller;

import java.util.UUID;

import io.labs64.paymentgateway.entity.CheckoutSessionEntity;
import io.labs64.paymentgateway.mapper.CheckoutSessionConfirmationMapper;
import io.labs64.paymentgateway.model.CheckoutSessionConfirmation;
import io.labs64.paymentgateway.service.CheckoutSessionConfirmationService;
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
class CheckoutSessionControllerTest {

    @Mock
    private CheckoutSessionConfirmationService service;

    @Mock
    private CheckoutSessionConfirmationMapper mapper;

    @InjectMocks
    private CheckoutSessionController controller;

    @Test
    void getCheckoutSessionConfirmationReturnsPublicConfirmationView() {
        final UUID sessionId = UUID.randomUUID();
        final CheckoutSessionEntity entity = CheckoutSessionEntity.builder().id(sessionId).build();
        final CheckoutSessionConfirmation response = new CheckoutSessionConfirmation();

        when(service.get(sessionId)).thenReturn(entity);
        when(mapper.toDto(entity)).thenReturn(response);

        final ResponseEntity<CheckoutSessionConfirmation> result = controller.getCheckoutSessionConfirmation(sessionId);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isSameAs(response);

        verify(service).get(sessionId);
        verify(mapper).toDto(entity);
        verifyNoMoreInteractions(service, mapper);
    }
}
