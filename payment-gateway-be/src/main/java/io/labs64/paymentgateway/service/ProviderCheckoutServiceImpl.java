package io.labs64.paymentgateway.service;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import io.labs64.paymentgateway.config.PaymentGatewayProperties;
import io.labs64.paymentgateway.entity.CheckoutSessionEntity;
import io.labs64.paymentgateway.entity.PaymentEntity;
import io.labs64.paymentgateway.entity.PaymentTransactionEntity;
import io.labs64.paymentgateway.exception.NotFoundException;
import io.labs64.paymentgateway.exception.ValidationException;
import io.labs64.paymentgateway.mapper.PaymentContextMapper;
import io.labs64.paymentgateway.psp.internal.PaymentProviderRegistry;
import io.labs64.paymentgateway.psp.spi.PaymentNextAction;
import io.labs64.paymentgateway.psp.spi.PaymentProvider;
import io.labs64.paymentgateway.psp.spi.PaymentResult;
import io.labs64.paymentgateway.psp.spi.ProviderExecutionException;
import io.labs64.paymentgateway.psp.spi.ProviderValidationException;
import io.labs64.paymentgateway.psp.spi.ProviderCheckoutContext;
import io.labs64.paymentgateway.psp.spi.ProviderCheckoutSupport;
import io.labs64.paymentgateway.repository.CheckoutSessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Default {@link ProviderCheckoutService} implementation.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProviderCheckoutServiceImpl implements ProviderCheckoutService {

    private final CheckoutSessionRepository checkoutSessionRepository;
    private final PaymentTransactionService transactionService;
    private final PaymentContextMapper paymentContextMapper;
    private final PaymentProviderRegistry providerRegistry;
    private final PaymentGatewayProperties properties;

    @Override
    @Transactional
    public URI complete(final String provider, final UUID sessionId, final Map<String, List<String>> queryParams) {
        final CheckoutSessionEntity session = getSession(sessionId);
        final PaymentTransactionEntity transaction = session.getPaymentTransaction();
        final PaymentEntity payment = session.getPayment();

        ensureProvider(provider, payment);
        final PaymentResult result;
        try {
            result = checkoutSupport(provider).completeCheckout(toContext(session, queryParams));
        } catch (ProviderValidationException ex) {
            log.warn(
                    "Payment provider checkout completion rejected: sessionId={}, paymentTransactionId={}, provider={}, message={}",
                    sessionId, transaction.getId(), provider, ex.getMessage());
            return fallbackRedirect();
        } catch (ProviderExecutionException ex) {
            log.warn(
                    "Payment provider checkout completion failed: sessionId={}, paymentTransactionId={}, provider={}, message={}",
                    sessionId, transaction.getId(), provider, ex.getMessage(), ex);
            recordProviderExecutionFailure(transaction, ex);
            return fallbackRedirect();
        }
        transactionService.applyResult(transaction, result);
        return withCheckoutIdentifiers(redirectFrom(result.nextAction()), session);
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
        final PaymentResult result;
        try {
            result = checkoutSupport(provider).cancelCheckout(toContext(session, queryParams));
        } catch (ProviderValidationException ex) {
            log.warn(
                    "Payment provider checkout cancellation rejected: sessionId={}, paymentTransactionId={}, provider={}, message={}",
                    sessionId, transaction.getId(), provider, ex.getMessage());
            return fallbackRedirect();
        } catch (ProviderExecutionException ex) {
            log.warn(
                    "Payment provider checkout cancellation failed: sessionId={}, paymentTransactionId={}, provider={}, message={}",
                    sessionId, transaction.getId(), provider, ex.getMessage(), ex);
            recordProviderExecutionFailure(transaction, ex);
            return fallbackRedirect();
        }
        transactionService.applyResult(transaction, result);
        return withCheckoutIdentifiers(redirectFrom(result.nextAction()), session);
    }

    private void recordProviderExecutionFailure(
            final PaymentTransactionEntity transaction,
            final ProviderExecutionException exception) {
        transactionService.recordProviderExecutionFailure(transaction, exception.failure());
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
        return fallbackRedirect();
    }

    private URI fallbackRedirect() {
        return properties.getCheckoutFallbackRedirectUrl();
    }

    private URI withCheckoutIdentifiers(final URI location, final CheckoutSessionEntity session) {
        return UriComponentsBuilder.fromUri(location)
                .queryParam("sessionId", session.getId())
                .build(true)
                .toUri();
    }

}
