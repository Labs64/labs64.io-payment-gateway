package io.labs64.paymentgateway.service;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import io.labs64.paymentgateway.entity.CheckoutSessionEntity;
import io.labs64.paymentgateway.entity.PaymentEntity;
import io.labs64.paymentgateway.entity.PaymentTransactionEntity;
import io.labs64.paymentgateway.exception.NotFoundException;
import io.labs64.paymentgateway.exception.PspException;
import io.labs64.paymentgateway.exception.ValidationException;
import io.labs64.paymentgateway.mapper.PaymentContextMapper;
import io.labs64.paymentgateway.model.PaymentStatus;
import io.labs64.paymentgateway.model.PaymentTransactionStatus;
import io.labs64.paymentgateway.model.PaymentType;
import io.labs64.paymentgateway.model.StatusDetails;
import io.labs64.paymentgateway.psp.internal.PaymentProviderRegistry;
import io.labs64.paymentgateway.psp.spi.PaymentNextAction;
import io.labs64.paymentgateway.psp.spi.PaymentProvider;
import io.labs64.paymentgateway.psp.spi.PaymentResult;
import io.labs64.paymentgateway.psp.spi.ProviderCheckoutContext;
import io.labs64.paymentgateway.psp.spi.ProviderCheckoutSupport;
import io.labs64.paymentgateway.repository.CheckoutSessionRepository;
import io.labs64.paymentgateway.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriComponentsBuilder;

import static io.labs64.paymentgateway.domain.PaymentTransactionStatuses.isTerminal;

/**
 * Default {@link ProviderCheckoutService} implementation.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProviderCheckoutServiceImpl implements ProviderCheckoutService {

    private static final URI FALLBACK_REDIRECT = URI.create("/");

    private final CheckoutSessionRepository checkoutSessionRepository;
    private final PaymentTransactionService transactionService;
    private final PaymentRepository paymentRepository;
    private final PaymentContextMapper paymentContextMapper;
    private final PaymentProviderRegistry providerRegistry;
    private final PaymentEventPublisher paymentEventPublisher;

    @Override
    @Transactional
    public URI complete(final String provider, final UUID sessionId, final Map<String, List<String>> queryParams) {
        final CheckoutSessionEntity session = getSession(sessionId);
        final PaymentTransactionEntity transaction = session.getPaymentTransaction();
        final PaymentEntity payment = session.getPayment();

        ensureProvider(provider, payment);
        if (!isTerminal(transaction.getStatus())) {
            final PaymentResult result;
            try {
                result = checkoutSupport(provider).completeCheckout(toContext(session, queryParams));
            } catch (PspException ex) {
                log.warn("PSP checkout completion failed: sessionId={}, paymentTransactionId={}, provider={}, message={}",
                        sessionId, transaction.getId(), provider, ex.getMessage(), ex);
                failTransaction(payment, transaction, ex);
                return FALLBACK_REDIRECT;
            }
            applyResult(payment, transaction, result);
            return withCheckoutIdentifiers(redirectFrom(result.nextAction()), session);
        }

        log.info("Ignoring duplicate checkout return: sessionId={}, paymentTransactionId={}, status={}",
                sessionId, transaction.getId(), transaction.getStatus());
        return FALLBACK_REDIRECT;
    }

    @Override
    @Transactional
    public URI cancel(
            final String provider,
            final UUID sessionId,
            final Map<String, List<String>> queryParams) {
        final CheckoutSessionEntity session = getSession(sessionId);
        final PaymentTransactionEntity transaction = session.getPaymentTransaction();
        final PaymentEntity payment = session.getPayment();

        ensureProvider(provider, payment);
        if (!isTerminal(transaction.getStatus())) {
            final PaymentResult result;
            try {
                result = checkoutSupport(provider).cancelCheckout(toContext(session, queryParams));
            } catch (PspException ex) {
                log.warn("PSP checkout cancellation failed: sessionId={}, paymentTransactionId={}, provider={}, message={}",
                        sessionId, transaction.getId(), provider, ex.getMessage(), ex);
                failTransaction(payment, transaction, ex);
                return FALLBACK_REDIRECT;
            }
            applyResult(payment, transaction, result);
            return withCheckoutIdentifiers(redirectFrom(result.nextAction()), session);
        }

        return FALLBACK_REDIRECT;
    }

    private void failTransaction(
            final PaymentEntity payment,
            final PaymentTransactionEntity transaction,
            final PspException exception) {
        final PaymentTransactionEntity failedTransaction = transactionService.update(
                transaction.getTenantId(),
                transaction.getId(),
                (pt) -> {
                    pt.setStatus(PaymentTransactionStatus.FAILED);
                    pt.setStatusDetails(new StatusDetails("PSP_ERROR", exception.getMessage()));
                    pt.setPspData(null);
                });
        paymentEventPublisher.publishFinalized(payment, failedTransaction);
    }

    private CheckoutSessionEntity getSession(final UUID sessionId) {
        return checkoutSessionRepository.findById(sessionId)
                .orElseThrow(() -> new NotFoundException("Checkout session not found for ID: " + sessionId));
    }

    private ProviderCheckoutSupport checkoutSupport(final String provider) {
        final PaymentProvider paymentProvider = providerRegistry.getProvider(provider);
        if (!(paymentProvider instanceof ProviderCheckoutSupport checkoutSupport)) {
            throw new ValidationException("Payment provider does not support checkout callbacks: " + provider);
        }
        return checkoutSupport;
    }

    private ProviderCheckoutContext toContext(
            final CheckoutSessionEntity session,
            final Map<String, List<String>> queryParams) {
        return new ProviderCheckoutContext(
                paymentContextMapper.toPayment(session.getPayment()),
                paymentContextMapper.toPaymentTransaction(session.getPaymentTransaction()),
                paymentContextMapper.toProviderConfig(session.getPayment().getPaymentProvider()),
                paymentContextMapper.toCheckoutSession(session),
                queryParams != null ? queryParams : Map.of());
    }

    private void applyResult(
            final PaymentEntity payment,
            final PaymentTransactionEntity transaction,
            final PaymentResult result) {
        final PaymentTransactionEntity updatedTransaction = transactionService.update(
                transaction.getTenantId(),
                transaction.getId(),
                (pt) -> {
                    pt.setStatus(result.status());
                    pt.setStatusDetails(toStatusDetails(result.statusDetails()));
                    pt.setPspData(result.pspData());
                });

        final boolean closesPayment = PaymentTransactionStatus.SUCCESS.equals(result.status())
                && PaymentType.ONE_TIME.equals(payment.getType());
        if (closesPayment) {
            payment.setStatus(PaymentStatus.CLOSED);
            paymentRepository.save(payment);
        }

        if (isTerminal(result.status())) {
            paymentEventPublisher.publishFinalized(payment, updatedTransaction);
        }
        if (closesPayment) {
            paymentEventPublisher.publishClosed(payment, updatedTransaction);
        }
    }

    private void ensureProvider(final String provider, final PaymentEntity payment) {
        if (payment.getPaymentProvider() == null) {
            throw new ValidationException("Payment provider is not available for payment: " + payment.getId());
        }

        final String expectedProvider = payment.getPaymentProvider().getProvider();
        if (!provider.equals(expectedProvider)) {
            throw new ValidationException("Checkout provider does not match payment provider.");
        }
    }

    private URI redirectFrom(final PaymentNextAction nextAction) {
        if (nextAction != null && nextAction.details() != null) {
            final Object url = nextAction.details().getOrDefault("url", nextAction.details().get("redirectUrl"));
            if (url instanceof String stringUrl && !stringUrl.isBlank()) {
                return URI.create(stringUrl);
            }
        }
        return FALLBACK_REDIRECT;
    }

    private URI withCheckoutIdentifiers(final URI location, final CheckoutSessionEntity session) {
        return UriComponentsBuilder.fromUri(location)
                .queryParam("sessionId", session.getId())
                .build(true)
                .toUri();
    }

    private StatusDetails toStatusDetails(final io.labs64.paymentgateway.psp.spi.StatusDetails source) {
        if (source == null) {
            return null;
        }
        return new StatusDetails(source.code(), source.message());
    }

}
