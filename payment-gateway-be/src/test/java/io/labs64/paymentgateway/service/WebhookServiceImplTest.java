package io.labs64.paymentgateway.service;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import io.labs64.paymentgateway.entity.PaymentEntity;
import io.labs64.paymentgateway.entity.PaymentProviderEntity;
import io.labs64.paymentgateway.entity.PaymentTransactionEntity;
import io.labs64.paymentgateway.exception.ConflictException;
import io.labs64.paymentgateway.exception.ValidationException;
import io.labs64.paymentgateway.mapper.PaymentContextMapper;
import io.labs64.paymentgateway.model.PaymentStatus;
import io.labs64.paymentgateway.model.PaymentTransactionStatus;
import io.labs64.paymentgateway.model.PaymentType;
import io.labs64.paymentgateway.model.StatusDetails;
import io.labs64.paymentgateway.psp.internal.PaymentProviderRegistry;
import io.labs64.paymentgateway.psp.spi.Payment;
import io.labs64.paymentgateway.psp.spi.PaymentProvider;
import io.labs64.paymentgateway.psp.spi.PaymentTransaction;
import io.labs64.paymentgateway.psp.spi.PaymentWebhookResult;
import io.labs64.paymentgateway.psp.spi.ProviderConfig;
import io.labs64.paymentgateway.psp.spi.WebhookRequest;
import io.labs64.paymentgateway.repository.CorrelationTraceRepository;
import io.labs64.paymentgateway.repository.PaymentRepository;
import io.labs64.paymentgateway.repository.PaymentTransactionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WebhookServiceImplTest {

    private static final String TENANT_ID = "tenant-a";
    private static final String PROVIDER = "noop";

    @Mock
    private PaymentProviderRegistry providerRegistry;

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private PaymentTransactionRepository paymentTransactionRepository;

    @Mock
    private CorrelationTraceRepository correlationTraceRepository;

    @Mock
    private PaymentContextMapper paymentContextMapper;

    @Mock
    private PaymentEventPublisher paymentEventPublisher;

    @Mock
    private PaymentProvider paymentProvider;

    @InjectMocks
    private WebhookServiceImpl service;

    @Test
    void processWebhookClosesOneTimePaymentAndPublishesFinalizedAndClosedEvents() {
        final PaymentEntity payment = payment(PROVIDER, PaymentStatus.READY);
        final PaymentTransactionEntity transaction = transaction(payment, PaymentTransactionStatus.PENDING);
        final WebhookRequest request = request(PROVIDER, transaction.getId());
        final PaymentWebhookResult result = successfulResult();

        stubTransactionLookup(request, transaction);
        stubMapper(payment, transaction);
        when(paymentProvider.handleWebhook(any())).thenReturn(result);

        final PaymentWebhookResult response = service.processWebhook(request);

        assertThat(response).isSameAs(result);
        assertThat(transaction.getStatus()).isEqualTo(PaymentTransactionStatus.SUCCESS);
        assertThat(transaction.getStatusDetails()).isEqualTo(new StatusDetails("SUCCESS", "Success"));
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.CLOSED);
        verify(paymentRepository).save(payment);
        verify(paymentEventPublisher).publishFinalized(payment, transaction);
        verify(paymentEventPublisher).publishClosed(payment, transaction);
    }

    @Test
    void processWebhookRejectsProviderMismatch() {
        final PaymentEntity payment = payment("stripe", PaymentStatus.READY);
        final PaymentTransactionEntity transaction = transaction(payment, PaymentTransactionStatus.PENDING);
        final WebhookRequest request = request(PROVIDER, transaction.getId());

        stubTransactionLookup(request, transaction);

        assertThatThrownBy(() -> service.processWebhook(request))
                .isInstanceOf(ValidationException.class);

        verify(paymentProvider, never()).handleWebhook(any());
        verify(paymentEventPublisher, never()).publishFinalized(any(), any());
    }

    @Test
    void processWebhookIgnoresDuplicateTerminalWebhookWithSameStatus() {
        final PaymentEntity payment = payment(PROVIDER, PaymentStatus.CLOSED);
        final PaymentTransactionEntity transaction = transaction(payment, PaymentTransactionStatus.SUCCESS);
        final WebhookRequest request = request(PROVIDER, transaction.getId());

        stubTransactionLookup(request, transaction);
        stubMapper(payment, transaction);
        when(paymentProvider.handleWebhook(any())).thenReturn(successfulResult());

        service.processWebhook(request);

        verify(paymentRepository, never()).save(any());
        verify(paymentEventPublisher, never()).publishFinalized(any(), any());
        verify(paymentEventPublisher, never()).publishClosed(any(), any());
    }

    @Test
    void processWebhookRejectsTerminalStatusChange() {
        final PaymentEntity payment = payment(PROVIDER, PaymentStatus.CLOSED);
        final PaymentTransactionEntity transaction = transaction(payment, PaymentTransactionStatus.SUCCESS);
        final WebhookRequest request = request(PROVIDER, transaction.getId());

        stubTransactionLookup(request, transaction);
        stubMapper(payment, transaction);
        when(paymentProvider.handleWebhook(any())).thenReturn(new PaymentWebhookResult(
                PROVIDER,
                PaymentTransactionStatus.FAILED,
                Map.of(),
                new io.labs64.paymentgateway.psp.spi.StatusDetails("FAILED", "Failed")));

        assertThatThrownBy(() -> service.processWebhook(request))
                .isInstanceOf(ConflictException.class);

        verify(paymentEventPublisher, never()).publishFinalized(any(), any());
    }

    private void stubTransactionLookup(final WebhookRequest request, final PaymentTransactionEntity transaction) {
        when(providerRegistry.getProvider(request.provider())).thenReturn(paymentProvider);
        when(paymentProvider.resolvePaymentTransactionId(request)).thenReturn(transaction.getId());
        when(paymentTransactionRepository.findById(transaction.getId())).thenReturn(Optional.of(transaction));
        when(correlationTraceRepository.findFirstByEntityTypeAndEntityIdOrderByCreatedAtDesc(
                anyString(),
                any(UUID.class))).thenReturn(Optional.empty());
    }

    private void stubMapper(final PaymentEntity payment, final PaymentTransactionEntity transaction) {
        when(paymentContextMapper.toPayment(payment)).thenReturn(new Payment(
                payment.getId(),
                PaymentType.ONE_TIME,
                payment.getDescription(),
                null,
                payment.getPurchaseOrder(),
                null,
                null,
                null));
        when(paymentContextMapper.toPaymentTransaction(transaction)).thenReturn(new PaymentTransaction(
                transaction.getId(),
                transaction.getStatus()));
        when(paymentContextMapper.toProviderConfig(payment.getPaymentProvider())).thenReturn(new ProviderConfig(
                PROVIDER,
                Map.of(),
                "Noop",
                "No operation provider"));
    }

    private static PaymentWebhookResult successfulResult() {
        return new PaymentWebhookResult(
                PROVIDER,
                PaymentTransactionStatus.SUCCESS,
                Map.of("providerReference", "noop-1"),
                new io.labs64.paymentgateway.psp.spi.StatusDetails("SUCCESS", "Success"));
    }

    private static PaymentEntity payment(final String provider, final PaymentStatus status) {
        return PaymentEntity.builder()
                .id(UUID.randomUUID())
                .tenantId(TENANT_ID)
                .paymentProvider(PaymentProviderEntity.builder()
                        .id(UUID.randomUUID())
                        .tenantId(TENANT_ID)
                        .provider(provider)
                        .build())
                .status(status)
                .purchaseOrder(Map.of("grossAmount", 3000L, "currency", "USD"))
                .build();
    }

    private static PaymentTransactionEntity transaction(
            final PaymentEntity payment,
            final PaymentTransactionStatus status) {
        return PaymentTransactionEntity.builder()
                .id(UUID.randomUUID())
                .tenantId(TENANT_ID)
                .payment(payment)
                .status(status)
                .build();
    }

    private static WebhookRequest request(final String provider, final UUID transactionId) {
        return new WebhookRequest(
                provider,
                new byte[0],
                Map.of("transactionId", transactionId.toString()),
                Map.of(),
                Map.of());
    }
}
