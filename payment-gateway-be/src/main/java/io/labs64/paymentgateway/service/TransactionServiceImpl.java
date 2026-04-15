package io.labs64.paymentgateway.service;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.labs64.paymentgateway.entity.TransactionEntity;
import io.labs64.paymentgateway.exception.NotFoundException;
import io.labs64.paymentgateway.mapper.TransactionMapper;
import io.labs64.paymentgateway.repository.TransactionRepository;
import io.labs64.paymentgateway.v1.model.TransactionResponse;
import lombok.RequiredArgsConstructor;

/**
 * Implementation of {@link TransactionService}.
 */
@Service
@RequiredArgsConstructor
public class TransactionServiceImpl implements TransactionService {

    private static final Logger log = LoggerFactory.getLogger(TransactionServiceImpl.class);

    private final TransactionRepository transactionRepository;
    private final TransactionMapper transactionMapper;

    @Override
    @Transactional(readOnly = true)
    public TransactionResponse getTransaction(final String tenantId, final UUID transactionId) {
        log.debug("Loading transaction for tenantId={}, transactionId={}", tenantId, transactionId);

        final TransactionEntity entity = transactionRepository.findByIdAndTenantId(transactionId, tenantId)
                .orElseThrow(() -> new NotFoundException(
                        "Transaction with ID '" + transactionId + "' was not found."));

        return transactionMapper.toTransactionResponse(entity);
    }
}
