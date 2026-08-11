package io.labs64.paymentgateway.service;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

import io.labs64.paymentgateway.entity.PaymentEntity;
import io.labs64.paymentgateway.entity.PaymentTransactionEntity;
import io.labs64.paymentgateway.exception.NotFoundException;
import io.labs64.paymentgateway.exception.ValidationException;
import io.labs64.paymentgateway.message.PaymentTransactionMessages;
import io.labs64.paymentgateway.model.PaymentStatus;
import io.labs64.paymentgateway.model.PaymentTransactionStatus;
import io.labs64.paymentgateway.model.StatusDetails;
import io.labs64.paymentgateway.psp.spi.PaymentResult;
import io.labs64.paymentgateway.repository.PaymentTransactionRepository;
import io.labs64.paymentgateway.service.filter.PaymentTransactionFilter;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentTransactionServiceImplTest {

    private static final String TENANT_ID = "tenant-a";

    @Mock
    private PaymentTransactionRepository repository;

    @Mock
    private PaymentTransactionMessages msg;

    @Mock
    private EntityManager entityManager;

    @Mock
    private PaymentEventPublisher paymentEventPublisher;

    @InjectMocks
    private PaymentTransactionServiceImpl service;

    @Test
    void findUsesTenantScopedRepositoryLookup() {
        final UUID id = UUID.randomUUID();
        final PaymentTransactionEntity entity = transaction();
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
    void listPassesNullFiltersWhenFilterIsNull() {
        when(repository.searchByTenantId(TENANT_ID, null, null, Pageable.unpaged()))
                .thenReturn(new PageImpl<>(List.of(transaction())));

        assertThat(service.list(TENANT_ID, null, Pageable.unpaged()).getContent()).hasSize(1);

        verify(repository).searchByTenantId(TENANT_ID, null, null, Pageable.unpaged());
    }

    @Test
    void listPassesPaymentIdAndStatusFilters() {
        final UUID paymentId = UUID.randomUUID();
        final PaymentTransactionFilter filter = new PaymentTransactionFilter(paymentId, PaymentTransactionStatus.SUCCESS);
        when(repository.searchByTenantId(TENANT_ID, paymentId, PaymentTransactionStatus.SUCCESS, Pageable.unpaged()))
                .thenReturn(new PageImpl<>(List.of(transaction())));

        service.list(TENANT_ID, filter, Pageable.unpaged());

        verify(repository).searchByTenantId(TENANT_ID, paymentId, PaymentTransactionStatus.SUCCESS, Pageable.unpaged());
    }

    @Test
    void createRejectsNullEntity() {
        when(msg.required()).thenReturn("required");

        assertThatThrownBy(() -> service.create(TENANT_ID, null))
                .isInstanceOf(ValidationException.class);

        verify(repository, never()).save(any());
    }

    @Test
    void createRejectsMissingPayment() {
        final PaymentTransactionEntity entity = transaction();
        entity.setPayment(null);
        when(msg.paymentRequired()).thenReturn("payment required");

        assertThatThrownBy(() -> service.create(TENANT_ID, entity))
                .isInstanceOf(ValidationException.class);

        verify(repository, never()).save(any());
    }

    @Test
    void createAssignsTenantAndPersistsTransaction() {
        final PaymentTransactionEntity entity = transaction();
        when(repository.save(entity)).thenReturn(entity);

        final PaymentTransactionEntity result = service.create(TENANT_ID, entity);

        assertThat(result.getTenantId()).isEqualTo(TENANT_ID);
        verify(repository).save(entity);
    }

    @Test
    void updateAppliesUpdaterToTenantScopedTransaction() {
        final UUID id = UUID.randomUUID();
        final PaymentTransactionEntity entity = transaction();
        entity.setStatus(PaymentTransactionStatus.PENDING);
        when(repository.findByIdAndTenantId(id, TENANT_ID)).thenReturn(Optional.of(entity));

        final PaymentTransactionEntity result = service.update(
                TENANT_ID,
                id,
                pt -> pt.setStatus(PaymentTransactionStatus.SUCCESS));

        assertThat(result.getStatus()).isEqualTo(PaymentTransactionStatus.SUCCESS);
    }

    @Test
    void updateThrowsNotFoundWhenTransactionDoesNotExistForTenant() {
        final UUID id = UUID.randomUUID();
        when(repository.findByIdAndTenantId(id, TENANT_ID)).thenReturn(Optional.empty());
        when(msg.notFound(id)).thenReturn("not found");

        assertThatThrownBy(() -> service.update(TENANT_ID, id, pt -> pt.setStatus(PaymentTransactionStatus.SUCCESS)))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void updateIfNonTerminalAppliesUpdaterToLockedTransaction() {
        final UUID id = UUID.randomUUID();
        final PaymentTransactionEntity entity = transaction();
        when(repository.findByIdAndTenantIdForUpdate(id, TENANT_ID)).thenReturn(Optional.of(entity));

        final PaymentTransactionEntity result = service.updateIfNonTerminal(
                TENANT_ID,
                id,
                pt -> pt.setStatus(PaymentTransactionStatus.SUCCESS));

        assertThat(result.getStatus()).isEqualTo(PaymentTransactionStatus.SUCCESS);
        verify(entityManager).refresh(entity);
    }

    @Test
    void updateIfNonTerminalDoesNotInvokeUpdaterForTerminalTransaction() {
        final UUID id = UUID.randomUUID();
        final PaymentTransactionEntity entity = transaction();
        entity.setStatus(PaymentTransactionStatus.SUCCESS);
        @SuppressWarnings("unchecked")
        final Consumer<PaymentTransactionEntity> updater = org.mockito.Mockito.mock(Consumer.class);
        when(repository.findByIdAndTenantIdForUpdate(id, TENANT_ID)).thenReturn(Optional.of(entity));

        final PaymentTransactionEntity result = service.updateIfNonTerminal(TENANT_ID, id, updater);

        assertThat(result).isSameAs(entity);
        verify(updater, never()).accept(any());
    }

    @Test
    void updateIfNonTerminalThrowsNotFoundWhenTransactionDoesNotExistForTenant() {
        final UUID id = UUID.randomUUID();
        when(repository.findByIdAndTenantIdForUpdate(id, TENANT_ID)).thenReturn(Optional.empty());
        when(msg.notFound(id)).thenReturn("not found");

        assertThatThrownBy(() -> service.updateIfNonTerminal(
                TENANT_ID,
                id,
                pt -> pt.setStatus(PaymentTransactionStatus.SUCCESS)))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void applyResultClosesOneTimePaymentAndPublishesLifecycleEvents() {
        final PaymentTransactionEntity transaction = transaction();
        final PaymentResult result = result(io.labs64.paymentgateway.psp.spi.PaymentTransactionStatus.SUCCESS);
        when(repository.findByIdAndTenantIdForUpdate(transaction.getId(), TENANT_ID))
                .thenReturn(Optional.of(transaction));

        final PaymentTransactionEntity updated = service.applyResult(transaction, result);

        assertThat(updated.getStatus()).isEqualTo(PaymentTransactionStatus.SUCCESS);
        assertThat(updated.getStatusDetails()).isEqualTo(new StatusDetails().code("SUCCESS").message("Provider result"));
        assertThat(updated.getPspData()).containsEntry("providerReference", "reference");
        assertThat(updated.getPayment().getStatus()).isEqualTo(PaymentStatus.CLOSED);
        verify(paymentEventPublisher).publishFinalized(updated.getPayment(), updated);
        verify(paymentEventPublisher).publishClosed(updated.getPayment(), updated);
    }

    @Test
    void applyResultKeepsRecurringPaymentReadyAfterSuccess() {
        final PaymentTransactionEntity transaction = transaction();
        transaction.getPayment().setRecurrence(Map.of("interval", "MONTH"));
        final PaymentResult result = result(io.labs64.paymentgateway.psp.spi.PaymentTransactionStatus.SUCCESS);
        when(repository.findByIdAndTenantIdForUpdate(transaction.getId(), TENANT_ID))
                .thenReturn(Optional.of(transaction));

        service.applyResult(transaction, result);

        assertThat(transaction.getPayment().getStatus()).isEqualTo(PaymentStatus.READY);
        verify(paymentEventPublisher).publishFinalized(transaction.getPayment(), transaction);
        verify(paymentEventPublisher, never()).publishClosed(any(), any());
    }

    @Test
    void applyResultFinalizesFailureWithoutChangingPaymentStatus() {
        final PaymentTransactionEntity transaction = transaction();
        final PaymentResult result = result(io.labs64.paymentgateway.psp.spi.PaymentTransactionStatus.FAILED);
        when(repository.findByIdAndTenantIdForUpdate(transaction.getId(), TENANT_ID))
                .thenReturn(Optional.of(transaction));

        service.applyResult(transaction, result);

        assertThat(transaction.getStatus()).isEqualTo(PaymentTransactionStatus.FAILED);
        assertThat(transaction.getPayment().getStatus()).isEqualTo(PaymentStatus.READY);
        verify(paymentEventPublisher).publishFinalized(transaction.getPayment(), transaction);
        verify(paymentEventPublisher, never()).publishClosed(any(), any());
    }

    @Test
    void applyResultIgnoresResultForTerminalTransaction() {
        final PaymentTransactionEntity transaction = transaction();
        transaction.setStatus(PaymentTransactionStatus.SUCCESS);
        final PaymentResult result = result(io.labs64.paymentgateway.psp.spi.PaymentTransactionStatus.FAILED);
        when(repository.findByIdAndTenantIdForUpdate(transaction.getId(), TENANT_ID))
                .thenReturn(Optional.of(transaction));

        service.applyResult(transaction, result);

        assertThat(transaction.getStatus()).isEqualTo(PaymentTransactionStatus.SUCCESS);
        verify(paymentEventPublisher, never()).publishFinalized(any(), any());
        verify(paymentEventPublisher, never()).publishClosed(any(), any());
    }

    private static PaymentResult result(
            final io.labs64.paymentgateway.psp.spi.PaymentTransactionStatus status) {
        return new PaymentResult(
                "provider",
                status,
                Map.of("providerReference", "reference"),
                new io.labs64.paymentgateway.psp.spi.StatusDetails(status.name(), "Provider result"),
                null);
    }

    private static PaymentTransactionEntity transaction() {
        return PaymentTransactionEntity.builder()
                .id(UUID.randomUUID())
                .tenantId(TENANT_ID)
                .payment(PaymentEntity.builder()
                        .id(UUID.randomUUID())
                        .tenantId(TENANT_ID)
                        .status(PaymentStatus.READY)
                        .build())
                .status(PaymentTransactionStatus.PENDING)
                .build();
    }
}
