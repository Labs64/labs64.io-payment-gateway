package io.labs64.paymentgateway.service;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import io.labs64.paymentgateway.entity.PaymentEntity;
import io.labs64.paymentgateway.entity.CheckoutSessionEntity;
import io.labs64.paymentgateway.entity.PaymentTransactionEntity;
import io.labs64.paymentgateway.exception.NotFoundException;
import io.labs64.paymentgateway.exception.ValidationException;
import io.labs64.paymentgateway.message.CheckoutSessionMessages;
import io.labs64.paymentgateway.repository.CheckoutSessionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CheckoutSessionServiceImplTest {

    private static final String TENANT_ID = "tenant-a";

    @Mock
    private CheckoutSessionRepository repository;

    @Mock
    private CheckoutSessionMessages msg;

    @InjectMocks
    private CheckoutSessionServiceImpl service;

    @Test
    void findUsesTenantScopedRepositoryLookup() {
        final UUID id = UUID.randomUUID();
        final CheckoutSessionEntity entity = session();
        when(repository.findByIdAndTenantId(id, TENANT_ID)).thenReturn(Optional.of(entity));

        assertThat(service.find(TENANT_ID, id)).containsSame(entity);
    }

    @Test
    void getThrowsNotFoundWhenTenantScopedLookupIsEmpty() {
        final UUID id = UUID.randomUUID();
        when(repository.findByIdAndTenantId(id, TENANT_ID)).thenReturn(Optional.empty());
        when(msg.notFound(id)).thenReturn("not found");

        assertThatThrownBy(() -> service.get(TENANT_ID, id))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void findByPaymentTransactionIdUsesTenantScopedLookup() {
        final UUID paymentTransactionId = UUID.randomUUID();
        final CheckoutSessionEntity entity = session();
        when(repository.findByTenantIdAndPaymentTransactionId(TENANT_ID, paymentTransactionId))
                .thenReturn(Optional.of(entity));

        assertThat(service.findByPaymentTransactionId(TENANT_ID, paymentTransactionId)).containsSame(entity);
    }

    @Test
    void createRejectsMissingTransaction() {
        when(msg.transactionRequired()).thenReturn("transaction required");

        assertThatThrownBy(() -> service.create(null, Map.of()))
                .isInstanceOf(ValidationException.class);

        verify(repository, never()).save(any());
    }

    @Test
    void createRejectsTransactionWithoutPayment() {
        final PaymentTransactionEntity transaction = transaction();
        transaction.setPayment(null);
        when(msg.paymentRequired()).thenReturn("payment required");

        assertThatThrownBy(() -> service.create(transaction, Map.of()))
                .isInstanceOf(ValidationException.class);

        verify(repository, never()).save(any());
    }

    @Test
    void createAssignsTransactionPaymentAndTenant() {
        final PaymentTransactionEntity transaction = transaction();
        final Map<String, Object> payload = Map.of("returnUrl", "https://checkout.example/return");
        when(repository.save(any(CheckoutSessionEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        final CheckoutSessionEntity result = service.create(transaction, payload);

        assertThat(result.getTenantId()).isEqualTo(TENANT_ID);
        assertThat(result.getPayment()).isSameAs(transaction.getPayment());
        assertThat(result.getPaymentId()).isEqualTo(transaction.getPayment().getId());
        assertThat(result.getPaymentTransaction()).isSameAs(transaction);
        assertThat(result.getPaymentTransactionId()).isEqualTo(transaction.getId());
        assertThat(result.getPayload()).isSameAs(payload);
    }

    @Test
    void updateNextActionUpdatesTenantScopedSession() {
        final UUID id = UUID.randomUUID();
        final CheckoutSessionEntity entity = session();
        final Map<String, Object> nextAction = Map.of("type", "REDIRECT");
        when(repository.findByIdAndTenantId(id, TENANT_ID)).thenReturn(Optional.of(entity));

        final CheckoutSessionEntity result = service.updateNextAction(TENANT_ID, id, nextAction);

        assertThat(result.getNextAction()).isSameAs(nextAction);
    }

    private static CheckoutSessionEntity session() {
        return CheckoutSessionEntity.builder()
                .id(UUID.randomUUID())
                .tenantId(TENANT_ID)
                .paymentId(UUID.randomUUID())
                .paymentTransactionId(UUID.randomUUID())
                .build();
    }

    private static PaymentTransactionEntity transaction() {
        final PaymentEntity payment = PaymentEntity.builder()
                .id(UUID.randomUUID())
                .tenantId(TENANT_ID)
                .build();

        return PaymentTransactionEntity.builder()
                .id(UUID.randomUUID())
                .tenantId(TENANT_ID)
                .payment(payment)
                .build();
    }
}
