package io.labs64.paymentgateway.service;

import java.util.Optional;
import java.util.UUID;

import io.labs64.paymentgateway.entity.CheckoutSessionEntity;
import io.labs64.paymentgateway.exception.NotFoundException;
import io.labs64.paymentgateway.message.CheckoutSessionMessages;
import io.labs64.paymentgateway.repository.CheckoutSessionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CheckoutSessionConfirmationServiceImplTest {

    @Mock
    private CheckoutSessionRepository repository;

    @Mock
    private CheckoutSessionMessages messages;

    @InjectMocks
    private CheckoutSessionConfirmationServiceImpl service;

    @Test
    void getReturnsCheckoutSessionConfirmationAggregate() {
        final UUID sessionId = UUID.randomUUID();
        final CheckoutSessionEntity session = CheckoutSessionEntity.builder().id(sessionId).build();
        when(repository.findConfirmationById(sessionId)).thenReturn(Optional.of(session));

        final CheckoutSessionEntity result = service.get(sessionId);

        assertThat(result).isSameAs(session);
        verify(repository).findConfirmationById(sessionId);
    }

    @Test
    void getThrowsNotFoundWhenCheckoutSessionDoesNotExist() {
        final UUID sessionId = UUID.randomUUID();
        when(repository.findConfirmationById(sessionId)).thenReturn(Optional.empty());
        when(messages.notFound(sessionId)).thenReturn("Checkout session not found.");

        assertThatThrownBy(() -> service.get(sessionId))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Checkout session not found.");
    }
}
