package io.labs64.paymentgateway.service;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import io.labs64.paymentgateway.config.PaymentGatewayProperties;
import io.labs64.paymentgateway.entity.CheckoutSessionEntity;
import io.labs64.paymentgateway.entity.PaymentEntity;
import io.labs64.paymentgateway.entity.PaymentProviderEntity;
import io.labs64.paymentgateway.entity.PaymentTransactionEntity;
import io.labs64.paymentgateway.mapper.PaymentContextMapper;
import io.labs64.paymentgateway.model.PaymentStatus;
import io.labs64.paymentgateway.model.PaymentTransactionStatus;
import io.labs64.paymentgateway.model.PurchaseOrder;
import io.labs64.paymentgateway.model.StatusDetails;
import io.labs64.paymentgateway.psp.internal.PaymentProviderRegistry;
import io.labs64.paymentgateway.psp.spi.CheckoutSession;
import io.labs64.paymentgateway.psp.spi.Payment;
import io.labs64.paymentgateway.psp.spi.PaymentNextAction;
import io.labs64.paymentgateway.psp.spi.PaymentNextActionType;
import io.labs64.paymentgateway.psp.spi.PaymentProvider;
import io.labs64.paymentgateway.psp.spi.PaymentResult;
import io.labs64.paymentgateway.psp.spi.ProviderExecutionException;
import io.labs64.paymentgateway.psp.spi.ProviderValidationException;
import io.labs64.paymentgateway.psp.spi.PaymentTransaction;
import io.labs64.paymentgateway.psp.spi.ProviderCheckoutContext;
import io.labs64.paymentgateway.psp.spi.ProviderCheckoutSupport;
import io.labs64.paymentgateway.psp.spi.ProviderConfig;
import io.labs64.paymentgateway.repository.CheckoutSessionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProviderCheckoutServiceImplTest {

    private static final String TENANT_ID = "tenant-a";
    private static final String PROVIDER = "paypal";
    private static final URI FALLBACK_REDIRECT = URI.create("https://checkout.example/error");

    @Mock
    private CheckoutSessionRepository checkoutSessionRepository;

    @Mock
    private PaymentTransactionService transactionService;

    @Mock
    private PaymentContextMapper paymentContextMapper;

    @Mock
    private PaymentProviderRegistry providerRegistry;

    @Mock
    private PaymentGatewayProperties properties;

    @Mock
    private CheckoutCapableProvider provider;

    @InjectMocks
    private ProviderCheckoutServiceImpl service;

    @Test
    void completeCapturesCheckoutAndRedirectsToReturnUrl() {
        final CheckoutSessionEntity session = session(PaymentTransactionStatus.PENDING);
        final ProviderCheckoutContext context = context(session);
        final PaymentResult result = new PaymentResult(
                PROVIDER,
                io.labs64.paymentgateway.psp.spi.PaymentTransactionStatus.SUCCESS,
                Map.of("orderId", "paypal-order"),
                new io.labs64.paymentgateway.psp.spi.StatusDetails("SUCCESS", "Captured"),
                redirect("https://checkout.example/return"));
        when(checkoutSessionRepository.findById(session.getId())).thenReturn(Optional.of(session));
        when(providerRegistry.getProvider(PROVIDER)).thenReturn(provider);
        mapContext(session, context);
        when(provider.completeCheckout(context)).thenReturn(result);

        final URI redirect = service.complete(PROVIDER, session.getId(), Map.of("token", List.of("paypal-order")));

        assertThat(redirect).isEqualTo(expectedRedirect(session, "https://checkout.example/return"));
        verify(transactionService).applyResult(session.getPaymentTransaction(), result);
    }

    @Test
    void cancelMarksCheckoutTransactionFailedAndRedirectsToCancelUrl() {
        final CheckoutSessionEntity session = session(PaymentTransactionStatus.PENDING);
        final ProviderCheckoutContext context = context(session);
        final PaymentResult result = new PaymentResult(
                PROVIDER,
                io.labs64.paymentgateway.psp.spi.PaymentTransactionStatus.FAILED,
                Map.of("orderId", "paypal-order"),
                new io.labs64.paymentgateway.psp.spi.StatusDetails("CANCELLED", "Cancelled"),
                redirect("https://checkout.example/cancel"));
        when(checkoutSessionRepository.findById(session.getId())).thenReturn(Optional.of(session));
        when(providerRegistry.getProvider(PROVIDER)).thenReturn(provider);
        mapContext(session, context);
        when(provider.cancelCheckout(context)).thenReturn(result);

        final URI redirect = service.cancel(PROVIDER, session.getId(), Map.of("token", List.of("paypal-order")));

        assertThat(redirect).isEqualTo(expectedRedirect(session, "https://checkout.example/cancel"));
        verify(transactionService).applyResult(session.getPaymentTransaction(), result);
    }

    @Test
    void completeKeepsTransactionPendingAndUsesFallbackRedirectWhenProviderOutcomeIsUnknown() {
        final CheckoutSessionEntity session = session(PaymentTransactionStatus.PENDING);
        final ProviderCheckoutContext context = context(session);
        when(checkoutSessionRepository.findById(session.getId())).thenReturn(Optional.of(session));
        when(providerRegistry.getProvider(PROVIDER)).thenReturn(provider);
        mapContext(session, context);
        when(provider.completeCheckout(context))
                .thenThrow(new ProviderExecutionException(
                        io.labs64.paymentgateway.psp.spi.ProviderExecutionFailure.UNAVAILABLE,
                        "PayPal order capture failed."));
        when(properties.getCheckoutFallbackRedirectUrl()).thenReturn(FALLBACK_REDIRECT);
        when(transactionService.recordProviderExecutionFailure(
                session.getPaymentTransaction(),
                io.labs64.paymentgateway.psp.spi.ProviderExecutionFailure.UNAVAILABLE))
                .thenReturn(session.getPaymentTransaction());

        final URI redirect = service.complete(PROVIDER, session.getId(), Map.of("token", List.of("paypal-order")));

        assertThat(redirect).isEqualTo(FALLBACK_REDIRECT);
        assertThat(session.getPaymentTransaction().getStatus()).isEqualTo(PaymentTransactionStatus.PENDING);
        assertThat(session.getPayment().getStatus()).isEqualTo(PaymentStatus.READY);
        verify(transactionService).recordProviderExecutionFailure(
                session.getPaymentTransaction(), io.labs64.paymentgateway.psp.spi.ProviderExecutionFailure.UNAVAILABLE);
    }

    @Test
    void completeValidationFailureLeavesTransactionDetailsUnchanged() {
        final CheckoutSessionEntity session = session(PaymentTransactionStatus.PENDING);
        final ProviderCheckoutContext context = context(session);
        session.getPaymentTransaction().setStatusDetails(
                new StatusDetails().code("AWAITING_CUSTOMER").message("Waiting for customer."));
        when(checkoutSessionRepository.findById(session.getId())).thenReturn(Optional.of(session));
        when(providerRegistry.getProvider(PROVIDER)).thenReturn(provider);
        mapContext(session, context);
        when(provider.completeCheckout(context))
                .thenThrow(new ProviderValidationException("Invalid callback token."));

        when(properties.getCheckoutFallbackRedirectUrl()).thenReturn(FALLBACK_REDIRECT);
        final URI redirect = service.complete(PROVIDER, session.getId(), context.queryParams());

        assertThat(redirect).isEqualTo(FALLBACK_REDIRECT);
        assertThat(session.getPaymentTransaction().getStatus()).isEqualTo(PaymentTransactionStatus.PENDING);
        assertThat(session.getPaymentTransaction().getStatusDetails()).isEqualTo(
                new StatusDetails().code("AWAITING_CUSTOMER").message("Waiting for customer."));
        verify(transactionService, never()).updateIfNonTerminal(any(), any(), any());
    }

    @Test
    void completeTerminalTransactionStillRedirectsWithoutUpdatingTransaction() {
        final CheckoutSessionEntity session = session(PaymentTransactionStatus.SUCCESS);
        final ProviderCheckoutContext context = context(session);
        final PaymentResult result = new PaymentResult(
                PROVIDER,
                io.labs64.paymentgateway.psp.spi.PaymentTransactionStatus.SUCCESS,
                Map.of("orderId", "paypal-order"),
                new io.labs64.paymentgateway.psp.spi.StatusDetails("SUCCESS", "Captured"),
                redirect("https://checkout.example/return"));
        when(checkoutSessionRepository.findById(session.getId())).thenReturn(Optional.of(session));
        when(providerRegistry.getProvider(PROVIDER)).thenReturn(provider);
        mapContext(session, context);
        when(provider.completeCheckout(context)).thenReturn(result);

        final URI redirect = service.complete(PROVIDER, session.getId(), context.queryParams());

        assertThat(redirect).isEqualTo(expectedRedirect(session, "https://checkout.example/return"));
        verify(transactionService).applyResult(session.getPaymentTransaction(), result);
    }

    private void mapContext(final CheckoutSessionEntity session, final ProviderCheckoutContext context) {
        when(paymentContextMapper.toPayment(session.getPayment())).thenReturn(context.payment());
        when(paymentContextMapper.toPaymentTransaction(session.getPaymentTransaction())).thenReturn(context.transaction());
        when(paymentContextMapper.toProviderConfig(session.getPayment().getPaymentProvider())).thenReturn(context.provider());
        when(paymentContextMapper.toCheckoutSession(session)).thenReturn(context.checkoutSession());
    }

    private static ProviderCheckoutContext context(final CheckoutSessionEntity session) {
        return new ProviderCheckoutContext(
                new Payment(
                        session.getPaymentId(),
                        io.labs64.paymentgateway.psp.spi.PaymentType.valueOf(
                                session.getPayment().getType().name()),
                        session.getPayment().getDescription(),
                        null,
                        new PurchaseOrder().currency("USD").grossAmount(3000L),
                        null,
                        null,
                        null),
                new PaymentTransaction(
                        session.getPaymentTransactionId(),
                        io.labs64.paymentgateway.psp.spi.PaymentTransactionStatus.valueOf(
                                session.getPaymentTransaction().getStatus().name())),
                new ProviderConfig(PROVIDER, Map.of("clientId", "client-id"), "PayPal", null),
                new CheckoutSession(session.getId(), session.getPayload(), null, null),
                Map.of("token", List.of("paypal-order")));
    }

    private static PaymentNextAction redirect(final String url) {
        return new PaymentNextAction(PaymentNextActionType.REDIRECT, Map.of("url", url));
    }

    private static URI expectedRedirect(final CheckoutSessionEntity session, final String url) {
        return URI.create(url
                + "?sessionId=" + session.getId());
    }

    private static CheckoutSessionEntity session(final PaymentTransactionStatus transactionStatus) {
        final PaymentProviderEntity paymentProvider = PaymentProviderEntity.builder()
                .id(UUID.randomUUID())
                .tenantId(TENANT_ID)
                .provider(PROVIDER)
                .active(true)
                .name("PayPal")
                .config(Map.of())
                .build();
        final PaymentEntity payment = PaymentEntity.builder()
                .id(UUID.randomUUID())
                .tenantId(TENANT_ID)
                .status(PaymentStatus.READY)
                .purchaseOrder(Map.of("currency", "USD", "grossAmount", 1000))
                .build();
        payment.setPaymentProvider(paymentProvider);
        final PaymentTransactionEntity transaction = PaymentTransactionEntity.builder()
                .id(UUID.randomUUID())
                .tenantId(TENANT_ID)
                .status(transactionStatus)
                .build();
        transaction.setPayment(payment);

        final CheckoutSessionEntity session = CheckoutSessionEntity.builder()
                .id(UUID.randomUUID())
                .tenantId(TENANT_ID)
                .payload(Map.of(
                        "returnUrl", "https://checkout.example/return",
                        "cancelUrl", "https://checkout.example/cancel"))
                .build();
        session.setPayment(payment);
        session.setPaymentTransaction(transaction);
        return session;
    }

    private interface CheckoutCapableProvider extends PaymentProvider, ProviderCheckoutSupport {
    }
}
