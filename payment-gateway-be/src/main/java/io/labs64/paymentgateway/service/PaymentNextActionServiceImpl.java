package io.labs64.paymentgateway.service;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.labs64.paymentgateway.entity.PaymentNextActionEntity;
import io.labs64.paymentgateway.entity.PaymentTransactionEntity;
import io.labs64.paymentgateway.psp.spi.PaymentNextAction;
import io.labs64.paymentgateway.repository.PaymentNextActionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentNextActionServiceImpl implements PaymentNextActionService {

    private final PaymentNextActionRepository repository;

    @Override
    @Transactional
    public PaymentNextActionEntity create(
            final PaymentTransactionEntity transaction,
            final PaymentNextAction nextAction) {
        final PaymentNextActionEntity entity = repository.save(PaymentNextActionEntity.builder()
                .transaction(transaction)
                .type(nextAction.type().name())
                .details(nextAction.details())
                .build());

        log.debug("Created payment next action for paymentTransactionId={}", transaction.getId());
        return entity;
    }

    @Override
    @Transactional
    public void deleteByTransactionId(final UUID transactionId) {
        repository.findByTransaction_Id(transactionId).ifPresent(repository::delete);
    }
}
