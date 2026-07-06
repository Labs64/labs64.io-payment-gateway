package io.labs64.paymentgateway.service;

import java.util.UUID;

import io.labs64.paymentgateway.entity.CheckoutSessionEntity;
import io.labs64.paymentgateway.exception.NotFoundException;
import io.labs64.paymentgateway.message.CheckoutSessionMessages;
import io.labs64.paymentgateway.repository.CheckoutSessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Default {@link CheckoutSessionConfirmationService} implementation.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CheckoutSessionConfirmationServiceImpl implements CheckoutSessionConfirmationService {

    private final CheckoutSessionRepository repository;
    private final CheckoutSessionMessages msg;

    @Override
    @Transactional(readOnly = true)
    public CheckoutSessionEntity get(final UUID sessionId) {
        log.debug("Get checkout session confirmation for sessionId={}", sessionId);
        return repository.findConfirmationById(sessionId)
                .orElseThrow(() -> new NotFoundException(msg.notFound(sessionId)));
    }
}
