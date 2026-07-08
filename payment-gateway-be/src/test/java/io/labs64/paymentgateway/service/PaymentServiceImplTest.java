package io.labs64.paymentgateway.service;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import io.labs64.paymentgateway.config.PaymentGatewayProperties.PaymentDefinition;
import io.labs64.paymentgateway.correlation.CorrelationEntityType;
import io.labs64.paymentgateway.entity.PaymentEntity;
import io.labs64.paymentgateway.entity.PaymentProviderEntity;
import io.labs64.paymentgateway.entity.CheckoutSessionEntity;
import io.labs64.paymentgateway.entity.PaymentTransactionEntity;
import io.labs64.paymentgateway.exception.ConflictException;
import io.labs64.paymentgateway.exception.PaymentNotPayableException;
import io.labs64.paymentgateway.exception.PspException;
import io.labs64.paymentgateway.mapper.PaymentContextMapper;
import io.labs64.paymentgateway.message.PaymentMessages;
import io.labs64.paymentgateway.model.PaymentStatus;
import io.labs64.paymentgateway.model.PaymentTransactionStatus;
import io.labs64.paymentgateway.model.StatusDetails;
import io.labs64.paymentgateway.psp.internal.PaymentProviderRegistry;
import io.labs64.paymentgateway.psp.spi.PaymentContext;
import io.labs64.paymentgateway.psp.spi.PaymentExecutionRequest;
import io.labs64.paymentgateway.psp.spi.PaymentNextAction;
import io.labs64.paymentgateway.psp.spi.PaymentProvider;
import io.labs64.paymentgateway.psp.spi.PaymentResult;
import io.labs64.paymentgateway.psp.spi.CheckoutSessionDraft;
import io.labs64.paymentgateway.psp.spi.CheckoutPreparationContext;
import io.labs64.paymentgateway.psp.spi.ProviderCheckoutSupport;
import io.labs64.paymentgateway.repository.PaymentRepository;
import io.labs64.paymentgateway.service.filter.PaymentFilter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentServiceImplTest {

    private static final String TENANT_ID = "tenant-a";
    private static final String PROVIDER = "stripe";
    private static final UUID PAYMENT_PROVIDER_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440010");

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private CorrelationTraceService correlationTraceService;

    @Mock
    private PaymentProviderService paymentProviderService;

    @Mock
    private PaymentTransactionService transactionService;

    @Mock
    private PaymentNextActionService paymentNextActionService;

    @Mock
    private CheckoutSessionService checkoutSessionService;

    @Mock
    private PaymentContextMapper paymentContextMapper;

    @Mock
    private PaymentDefinitionService paymentDefinitionService;

    @Mock
    private PaymentProviderRegistry providerRegistry;

    @Mock
    private PaymentEventPublisher paymentEventPublisher;

    @Mock
    private PaymentMessages msg;

    @Mock
    private PaymentProvider pspProvider;

    @Mock
    private CheckoutCapableProvider checkoutCapableProvider;

    @InjectMocks
    private PaymentServiceImpl service;

    @Test
    void listPassesNullStatusWhenFilterIsNull() {
        when(paymentRepository.searchByTenantId(TENANT_ID, null, Pageable.unpaged()))
                .thenReturn(new PageImpl<>(java.util.List.of(payment())));

        assertThat(service.list(TENANT_ID, null, Pageable.unpaged()).getContent()).hasSize(1);

        verify(paymentRepository).searchByTenantId(TENANT_ID, null, Pageable.unpaged());
    }

    @Test
    void listPassesStatusFromFilter() {
        when(paymentRepository.searchByTenantId(TENANT_ID, PaymentStatus.READY, Pageable.unpaged()))
                .thenReturn(new PageImpl<>(java.util.List.of(payment())));

        service.list(TENANT_ID, new PaymentFilter(PaymentStatus.READY), Pageable.unpaged());

        verify(paymentRepository).searchByTenantId(TENANT_ID, PaymentStatus.READY, Pageable.unpaged());
    }

    @Test
    void createAssignsTenantPaymentProviderAndReadyStatus() {
        final PaymentEntity input = PaymentEntity.builder()
                .id(UUID.randomUUID())
                .purchaseOrder(Map.of("grossAmount", 3000L))
                .build();
        final PaymentProviderEntity paymentProvider = paymentProvider(true);
        when(paymentProviderService.get(TENANT_ID, PAYMENT_PROVIDER_ID)).thenReturn(paymentProvider);
        when(paymentRepository.save(input)).thenReturn(input);

        final PaymentEntity result = service.create(TENANT_ID, PAYMENT_PROVIDER_ID, input);

        assertThat(result.getTenantId()).isEqualTo(TENANT_ID);
        assertThat(result.getPaymentProvider()).isSameAs(paymentProvider);
        assertThat(result.getStatus()).isEqualTo(PaymentStatus.READY);
        verify(paymentRepository).save(input);
        verify(correlationTraceService).attach(CorrelationEntityType.PAYMENT, input.getId());
        verify(paymentEventPublisher).publishCreated(input);
    }

    @Test
    void createRejectsInactivePaymentProvider() {
        when(paymentProviderService.get(TENANT_ID, PAYMENT_PROVIDER_ID)).thenReturn(paymentProvider(false));
        when(msg.inactivePaymentProvider(PROVIDER)).thenReturn("inactive");

        assertThatThrownBy(() -> service.create(TENANT_ID, PAYMENT_PROVIDER_ID, payment()))
                .isInstanceOf(ConflictException.class);

        verify(paymentRepository, never()).save(any());
    }

    @Test
    void payExecutesProviderAndClosesOneTimePaymentOnSuccess() {
        final PaymentEntity payment = payment();
        final PaymentTransactionEntity transaction = transaction(payment);
        final PaymentContext context = new PaymentContext(null, null, null);
        final PaymentResult result = new PaymentResult(
                PROVIDER,
                PaymentTransactionStatus.SUCCESS,
                Map.of("pspReference", "ok"),
                new io.labs64.paymentgateway.psp.spi.StatusDetails("SUCCESS", "Success"),
                null);

        when(paymentRepository.findByIdAndTenantId(payment.getId(), TENANT_ID)).thenReturn(Optional.of(payment));
        when(transactionService.create(any(), any())).thenReturn(transaction);
        when(paymentDefinitionService.findEnabled(PROVIDER)).thenReturn(Optional.of(definition()));
        when(providerRegistry.getProvider(PROVIDER)).thenReturn(pspProvider);
        when(paymentContextMapper.toContext(
                payment,
                transaction,
                payment.getPaymentProvider(),
                null,
                PaymentExecutionRequest.empty())).thenReturn(context);
        when(pspProvider.execute(context)).thenReturn(result);
        when(transactionService.update(any(), any(), any())).thenAnswer(invocation -> {
            final java.util.function.Consumer<PaymentTransactionEntity> updater = invocation.getArgument(2);
            updater.accept(transaction);
            return transaction;
        });

        final PayPaymentResponse response = service.pay(TENANT_ID, payment.getId());

        assertThat(response.payment()).isSameAs(payment);
        assertThat(response.transaction().getStatus()).isEqualTo(PaymentTransactionStatus.SUCCESS);
        assertThat(response.transaction().getStatusDetails()).isEqualTo(new StatusDetails("SUCCESS", "Success"));
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.CLOSED);
        verify(correlationTraceService).attach(CorrelationEntityType.PAYMENT_TRANSACTION, transaction.getId());
        verify(paymentEventPublisher).publishFinalized(payment, transaction);
        verify(paymentEventPublisher).publishClosed(payment, transaction);
    }

    @Test
    void payDoesNotRejectInactiveTenantPaymentProviderForExistingPayment() {
        final PaymentEntity payment = payment();
        payment.getPaymentProvider().setActive(false);
        final PaymentTransactionEntity transaction = transaction(payment);
        final PaymentContext context = new PaymentContext(null, null, null);

        when(paymentRepository.findByIdAndTenantId(payment.getId(), TENANT_ID)).thenReturn(Optional.of(payment));
        when(transactionService.create(any(), any())).thenReturn(transaction);
        when(paymentDefinitionService.findEnabled(PROVIDER)).thenReturn(Optional.of(definition()));
        when(providerRegistry.getProvider(PROVIDER)).thenReturn(pspProvider);
        when(paymentContextMapper.toContext(
                payment,
                transaction,
                payment.getPaymentProvider(),
                null,
                PaymentExecutionRequest.empty())).thenReturn(context);
        when(pspProvider.execute(context)).thenReturn(new PaymentResult(
                PROVIDER,
                PaymentTransactionStatus.SUCCESS,
                Map.of(),
                null,
                null));
        when(transactionService.update(any(), any(), any())).thenReturn(transaction);

        service.pay(TENANT_ID, payment.getId());

        verify(pspProvider).execute(context);
    }

    @Test
    void payCreatesCheckoutSessionForCheckoutProviderAndStoresNextActionOnSession() {
        final PaymentEntity payment = payment();
        final PaymentTransactionEntity transaction = transaction(payment);
        final PaymentExecutionRequest executionRequest = new PaymentExecutionRequest(Map.of(
                "returnUrl", "https://checkout.example/return"));
        final CheckoutPreparationContext preparationContext = new CheckoutPreparationContext(
                null,
                null,
                null,
                executionRequest);
        final CheckoutSessionDraft draft = new CheckoutSessionDraft(executionRequest.checkout(), null);
        final CheckoutSessionEntity session = CheckoutSessionEntity.builder()
                .id(UUID.randomUUID())
                .tenantId(TENANT_ID)
                .paymentId(payment.getId())
                .paymentTransactionId(transaction.getId())
                .payload(draft.payload())
                .build();
        final PaymentContext context = new PaymentContext(null, null, null);
        final PaymentNextAction nextAction = new PaymentNextAction(
                io.labs64.paymentgateway.model.NextAction.TypeEnum.REDIRECT,
                Map.of("redirectUrl", "https://paypal.example/approve"));

        when(paymentRepository.findByIdAndTenantId(payment.getId(), TENANT_ID)).thenReturn(Optional.of(payment));
        when(transactionService.create(any(), any())).thenReturn(transaction);
        when(paymentDefinitionService.findEnabled(PROVIDER)).thenReturn(Optional.of(definition()));
        when(providerRegistry.getProvider(PROVIDER)).thenReturn(checkoutCapableProvider);
        when(paymentContextMapper.toCheckoutPreparationContext(payment, transaction, payment.getPaymentProvider(), executionRequest))
                .thenReturn(preparationContext);
        when(checkoutCapableProvider.prepareCheckoutSession(preparationContext)).thenReturn(Optional.of(draft));
        when(checkoutSessionService.create(transaction, draft.payload(), draft.expiresAt())).thenReturn(session);
        when(paymentContextMapper.toContext(payment, transaction, payment.getPaymentProvider(), session, executionRequest))
                .thenReturn(context);
        when(checkoutCapableProvider.execute(context)).thenReturn(new PaymentResult(
                PROVIDER,
                PaymentTransactionStatus.PENDING,
                Map.of("orderId", "paypal-order"),
                null,
                nextAction));
        when(transactionService.update(any(), any(), any())).thenAnswer(invocation -> {
            final java.util.function.Consumer<PaymentTransactionEntity> updater = invocation.getArgument(2);
            updater.accept(transaction);
            return transaction;
        });

        final PayPaymentResponse response = service.pay(TENANT_ID, payment.getId(), executionRequest);

        assertThat(response.nextAction()).isSameAs(nextAction);
        verify(checkoutSessionService).create(transaction, draft.payload(), draft.expiresAt());
        verify(checkoutSessionService).updateNextAction(
                eq(TENANT_ID),
                eq(session.getId()),
                eq(Map.of("type", "REDIRECT", "details", Map.of("redirectUrl", "https://paypal.example/approve"))));
        verify(paymentNextActionService, never()).create(any(), any());
    }

    @Test
    void payCreatesFailedTransactionWhenPaymentDefinitionIsDisabled() {
        final PaymentEntity payment = payment();
        final PaymentTransactionEntity transaction = transaction(payment);
        when(paymentRepository.findByIdAndTenantId(payment.getId(), TENANT_ID)).thenReturn(Optional.of(payment));
        when(transactionService.create(any(), any())).thenReturn(transaction);
        when(paymentDefinitionService.findEnabled(PROVIDER)).thenReturn(Optional.empty());
        when(msg.providerDisabled(PROVIDER)).thenReturn("disabled");
        when(transactionService.update(any(), any(), any())).thenAnswer(invocation -> {
            final java.util.function.Consumer<PaymentTransactionEntity> updater = invocation.getArgument(2);
            updater.accept(transaction);
            return transaction;
        });

        final PayPaymentResponse response = service.pay(TENANT_ID, payment.getId());

        assertThat(response.transaction().getStatus()).isEqualTo(PaymentTransactionStatus.FAILED);
        assertThat(response.transaction().getStatusDetails().getCode()).isEqualTo("PAYMENT_PROVIDER_DISABLED");
        verify(providerRegistry, never()).getProvider(any());
        verify(paymentEventPublisher).publishFinalized(payment, transaction);
    }

    @Test
    void payCreatesFailedTransactionWhenProviderExecutionFails() {
        final PaymentEntity payment = payment();
        final PaymentTransactionEntity transaction = transaction(payment);
        final PaymentContext context = new PaymentContext(null, null, null);

        when(paymentRepository.findByIdAndTenantId(payment.getId(), TENANT_ID)).thenReturn(Optional.of(payment));
        when(transactionService.create(any(), any())).thenReturn(transaction);
        when(paymentDefinitionService.findEnabled(PROVIDER)).thenReturn(Optional.of(definition()));
        when(providerRegistry.getProvider(PROVIDER)).thenReturn(pspProvider);
        when(paymentContextMapper.toContext(
                payment,
                transaction,
                payment.getPaymentProvider(),
                null,
                PaymentExecutionRequest.empty())).thenReturn(context);
        when(pspProvider.execute(context)).thenThrow(new PspException("PayPal order creation failed."));
        when(transactionService.update(any(), any(), any())).thenAnswer(invocation -> {
            final java.util.function.Consumer<PaymentTransactionEntity> updater = invocation.getArgument(2);
            updater.accept(transaction);
            return transaction;
        });

        final PayPaymentResponse response = service.pay(TENANT_ID, payment.getId());

        assertThat(response.transaction().getStatus()).isEqualTo(PaymentTransactionStatus.FAILED);
        assertThat(response.transaction().getStatusDetails()).isEqualTo(
                new StatusDetails("PSP_ERROR", "PayPal order creation failed."));
        assertThat(response.nextAction()).isNull();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.READY);
        verify(paymentEventPublisher).publishFinalized(payment, transaction);
        verify(paymentEventPublisher, never()).publishClosed(any(), any());
        verify(paymentNextActionService, never()).create(any(), any());
    }

    @Test
    void payRejectsNotReadyPaymentBeforeCreatingTransaction() {
        final PaymentEntity payment = payment();
        payment.setStatus(PaymentStatus.CLOSED);
        when(paymentRepository.findByIdAndTenantId(payment.getId(), TENANT_ID)).thenReturn(Optional.of(payment));
        when(msg.notPayable(payment.getId())).thenReturn("not payable");

        assertThatThrownBy(() -> service.pay(TENANT_ID, payment.getId()))
                .isInstanceOf(PaymentNotPayableException.class);

        verify(transactionService, never()).create(any(), any());
    }

    @Test
    void updateRejectsClosedPayment() {
        final PaymentEntity payment = payment();
        payment.setStatus(PaymentStatus.CLOSED);
        when(paymentRepository.findByIdAndTenantId(payment.getId(), TENANT_ID)).thenReturn(Optional.of(payment));
        when(msg.cannotUpdateClosed(payment.getId())).thenReturn("closed");

        assertThatThrownBy(() -> service.update(TENANT_ID, payment.getId(), p -> p.setStatus(PaymentStatus.READY)))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void closeSetsClosedStatusAndPublishesClosedEvent() {
        final PaymentEntity payment = payment();
        when(paymentRepository.findByIdAndTenantId(payment.getId(), TENANT_ID)).thenReturn(Optional.of(payment));

        final PaymentEntity result = service.close(TENANT_ID, payment.getId());

        assertThat(result.getStatus()).isEqualTo(PaymentStatus.CLOSED);
        verify(paymentEventPublisher).publishClosed(payment, null);
    }

    @Test
    void createPendingTransactionUsesTenantAndPayment() {
        final PaymentEntity payment = payment();
        final PaymentTransactionEntity transaction = transaction(payment);
        when(paymentRepository.findByIdAndTenantId(payment.getId(), TENANT_ID)).thenReturn(Optional.of(payment));
        when(transactionService.create(any(), any())).thenReturn(transaction);
        when(paymentDefinitionService.findEnabled(PROVIDER)).thenReturn(Optional.empty());
        when(msg.providerDisabled(PROVIDER)).thenReturn("disabled");
        when(transactionService.update(any(), any(), any())).thenReturn(transaction);

        service.pay(TENANT_ID, payment.getId());

        final ArgumentCaptor<PaymentTransactionEntity> captor = ArgumentCaptor.forClass(PaymentTransactionEntity.class);
        verify(transactionService).create(eq(TENANT_ID), captor.capture());
        assertThat(captor.getValue().getTenantId()).isEqualTo(TENANT_ID);
        assertThat(captor.getValue().getPayment()).isSameAs(payment);
        assertThat(captor.getValue().getStatus()).isEqualTo(PaymentTransactionStatus.PENDING);
    }

    private static PaymentEntity payment() {
        return PaymentEntity.builder()
                .id(UUID.randomUUID())
                .tenantId(TENANT_ID)
                .paymentProvider(paymentProvider(true))
                .status(PaymentStatus.READY)
                .purchaseOrder(Map.of("grossAmount", 3000L, "currency", "USD"))
                .build();
    }

    private static PaymentProviderEntity paymentProvider(final boolean active) {
        return PaymentProviderEntity.builder()
                .id(UUID.randomUUID())
                .tenantId(TENANT_ID)
                .provider(PROVIDER)
                .active(active)
                .build();
    }

    private static PaymentTransactionEntity transaction(final PaymentEntity payment) {
        return PaymentTransactionEntity.builder()
                .id(UUID.randomUUID())
                .tenantId(TENANT_ID)
                .payment(payment)
                .status(PaymentTransactionStatus.PENDING)
                .build();
    }

    private static PaymentDefinition definition() {
        final PaymentDefinition definition = new PaymentDefinition();
        definition.setProvider(PROVIDER);
        definition.setEnabled(true);
        return definition;
    }

    private interface CheckoutCapableProvider extends PaymentProvider, ProviderCheckoutSupport {
    }
}
