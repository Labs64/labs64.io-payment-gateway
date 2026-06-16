package io.labs64.paymentgateway.service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import io.labs64.paymentgateway.entity.PaymentEntity;
import io.labs64.paymentgateway.entity.PaymentTransactionEntity;
import io.labs64.paymentgateway.exception.NotFoundException;
import io.labs64.paymentgateway.exception.ValidationException;
import io.labs64.paymentgateway.message.PaymentTransactionMessages;
import io.labs64.paymentgateway.model.PaymentTransactionStatus;
import io.labs64.paymentgateway.repository.PaymentTransactionRepository;
import io.labs64.paymentgateway.service.filter.PaymentTransactionFilter;
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

    private static PaymentTransactionEntity transaction() {
        return PaymentTransactionEntity.builder()
                .id(UUID.randomUUID())
                .payment(PaymentEntity.builder().id(UUID.randomUUID()).build())
                .status(PaymentTransactionStatus.PENDING)
                .build();
    }
}
