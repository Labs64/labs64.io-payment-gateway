package io.labs64.paymentgateway.service;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import io.labs64.paymentgateway.correlation.CorrelationEntityType;
import io.labs64.paymentgateway.entity.PaymentEntity;
import io.labs64.paymentgateway.entity.PaymentProviderEntity;
import io.labs64.paymentgateway.entity.PaymentTransactionEntity;
import io.labs64.paymentgateway.exception.ValidationException;
import io.labs64.paymentgateway.mapper.PaymentContextMapper;
import io.labs64.paymentgateway.model.PaymentStatus;
import io.labs64.paymentgateway.model.PaymentTransactionStatus;
import io.labs64.paymentgateway.psp.internal.PaymentProviderRegistry;
import io.labs64.paymentgateway.psp.spi.PaymentProvider;
import io.labs64.paymentgateway.psp.spi.PaymentTransaction;
import io.labs64.paymentgateway.psp.spi.PaymentWebhookResult;
import io.labs64.paymentgateway.psp.spi.ProviderConfig;
import io.labs64.paymentgateway.psp.spi.ProviderExecutionException;
import io.labs64.paymentgateway.psp.spi.ProviderWebhookSupport;
import io.labs64.paymentgateway.psp.spi.WebhookRequest;
import io.labs64.paymentgateway.psp.spi.WebhookRejectedException;
import io.labs64.paymentgateway.repository.CorrelationTraceRepository;
import io.labs64.paymentgateway.repository.PaymentTransactionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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
    private PaymentTransactionRepository paymentTransactionRepository;

    @Mock
    private PaymentTransactionService transactionService;

    @Mock
    private CorrelationTraceRepository correlationTraceRepository;

    @Mock
    private PaymentContextMapper paymentContextMapper;

    @Mock
    private WebhookCapableProvider paymentProvider;

    @InjectMocks
    private WebhookServiceImpl service;

    @Test
    void processWebhookDelegatesResultApplication() {
        final PaymentEntity payment = payment(PROVIDER, PaymentStatus.READY);
        final PaymentTransactionEntity transaction = transaction(payment, PaymentTransactionStatus.PENDING);
        final WebhookRequest request = request(PROVIDER);
        final PaymentWebhookResult result = successfulResult();

        stubTransactionLookup(request, transaction);
        stubMapper(payment, transaction);
        when(paymentProvider.handleWebhook(any())).thenReturn(result);

        final PaymentWebhookResult response = service.processWebhook(request);

        assertThat(response).isSameAs(result);
        verify(transactionService).applyResult(transaction, result);
    }

    @Test
    void processWebhookRejectsProviderMismatch() {
        final PaymentEntity payment = payment("stripe", PaymentStatus.READY);
        final PaymentTransactionEntity transaction = transaction(payment, PaymentTransactionStatus.PENDING);
        final WebhookRequest request = request(PROVIDER);

        stubTransactionLookup(request, transaction);

        assertThatThrownBy(() -> service.processWebhook(request))
                .isInstanceOf(ValidationException.class);

        verify(paymentProvider, never()).handleWebhook(any());
        verify(transactionService, never()).applyResult(any(), any());
    }

    @Test
    void processWebhookRejectsProviderWithoutWebhookCapability() {
        final WebhookRequest request = request(PROVIDER);
        when(providerRegistry.getProvider(request.provider())).thenReturn(new NonWebhookProvider());

        assertThatThrownBy(() -> service.processWebhook(request))
                .isInstanceOf(ValidationException.class);

        verify(paymentTransactionRepository, never()).findById(any());
    }

    @Test
    void processWebhookSkipsProviderForDuplicateTerminalResult() {
        final PaymentEntity payment = payment(PROVIDER, PaymentStatus.CLOSED);
        final PaymentTransactionEntity transaction = transaction(payment, PaymentTransactionStatus.SUCCESS);
        final WebhookRequest request = request(PROVIDER);

        stubTransactionLookup(request, transaction);

        final PaymentWebhookResult response = service.processWebhook(request);

        assertThat(response.status()).isEqualTo(
                io.labs64.paymentgateway.psp.spi.PaymentTransactionStatus.SUCCESS);
        verify(paymentProvider, never()).handleWebhook(any());
        verify(transactionService, never()).applyResult(any(), any());
    }

    @Test
    void processWebhookSkipsProviderForFailedTerminalTransaction() {
        final PaymentEntity payment = payment(PROVIDER, PaymentStatus.READY);
        final PaymentTransactionEntity transaction = transaction(payment, PaymentTransactionStatus.FAILED);
        final WebhookRequest request = request(PROVIDER);

        stubTransactionLookup(request, transaction);

        final PaymentWebhookResult response = service.processWebhook(request);

        assertThat(response.status()).isEqualTo(
                io.labs64.paymentgateway.psp.spi.PaymentTransactionStatus.FAILED);
        verify(paymentProvider, never()).handleWebhook(any());
        verify(transactionService, never()).applyResult(any(), any());
    }

    @Test
    void processWebhookRecordsTechnicalDetailsWithoutFinalizingTransaction() {
        final PaymentEntity payment = payment(PROVIDER, PaymentStatus.READY);
        final PaymentTransactionEntity transaction = transaction(payment, PaymentTransactionStatus.PENDING);
        final WebhookRequest request = request(PROVIDER);

        stubTransactionLookup(request, transaction);
        stubMapper(payment, transaction);
        when(paymentProvider.handleWebhook(any())).thenThrow(new ProviderExecutionException(
                io.labs64.paymentgateway.psp.spi.ProviderExecutionFailure.UNAVAILABLE,
                "Provider unavailable."));
        when(transactionService.recordProviderExecutionFailure(
                transaction,
                io.labs64.paymentgateway.psp.spi.ProviderExecutionFailure.UNAVAILABLE)).thenReturn(transaction);

        assertThatThrownBy(() -> service.processWebhook(request))
                .isInstanceOf(ProviderExecutionException.class);

        assertThat(transaction.getStatus()).isEqualTo(PaymentTransactionStatus.PENDING);
        verify(transactionService).recordProviderExecutionFailure(
                transaction, io.labs64.paymentgateway.psp.spi.ProviderExecutionFailure.UNAVAILABLE);
        verify(transactionService, never()).applyResult(any(), any());
    }

    @Test
    void processWebhookLeavesTransactionUnchangedWhenProviderRejectsWebhook() {
        final PaymentEntity payment = payment(PROVIDER, PaymentStatus.READY);
        final PaymentTransactionEntity transaction = transaction(payment, PaymentTransactionStatus.PENDING);
        final WebhookRequest request = request(PROVIDER);

        stubTransactionLookup(request, transaction);
        stubMapper(payment, transaction);
        when(paymentProvider.handleWebhook(any()))
                .thenThrow(new WebhookRejectedException("Webhook verification failed."));

        assertThatThrownBy(() -> service.processWebhook(request))
                .isInstanceOf(WebhookRejectedException.class)
                .hasMessage("Webhook verification failed.");

        assertThat(transaction.getStatus()).isEqualTo(PaymentTransactionStatus.PENDING);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.READY);
        verify(transactionService, never()).applyResult(any(), any());
    }

    private void stubTransactionLookup(final WebhookRequest request, final PaymentTransactionEntity transaction) {
        when(providerRegistry.getProvider(request.provider())).thenReturn(paymentProvider);
        when(paymentProvider.extractPaymentTransactionId(request)).thenReturn(transaction.getId());
        when(paymentTransactionRepository.findById(transaction.getId())).thenReturn(Optional.of(transaction));
        when(correlationTraceRepository.findFirstByEntityTypeAndEntityIdOrderByCreatedAtDesc(
                any(CorrelationEntityType.class),
                any(UUID.class))).thenReturn(Optional.empty());
    }

    private void stubMapper(final PaymentEntity payment, final PaymentTransactionEntity transaction) {
        when(paymentContextMapper.toPaymentTransaction(transaction)).thenReturn(new PaymentTransaction(
                transaction.getId(),
                io.labs64.paymentgateway.psp.spi.PaymentTransactionStatus.valueOf(transaction.getStatus().name())));
        when(paymentContextMapper.toProviderConfig(payment.getPaymentProvider())).thenReturn(new ProviderConfig(
                PROVIDER,
                Map.of(),
                "Noop",
                "No operation provider"));
    }

    private static PaymentWebhookResult successfulResult() {
        return new PaymentWebhookResult(
                PROVIDER,
                io.labs64.paymentgateway.psp.spi.PaymentTransactionStatus.SUCCESS,
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

    private static WebhookRequest request(final String provider) {
        return new WebhookRequest(
                provider,
                "",
                Map.of(),
                Map.of());
    }

    private interface WebhookCapableProvider extends PaymentProvider, ProviderWebhookSupport {
    }

    private static class NonWebhookProvider implements PaymentProvider {

        @Override
        public String provider() {
            return PROVIDER;
        }

        @Override
        public io.labs64.paymentgateway.psp.spi.PaymentResult execute(final io.labs64.paymentgateway.psp.spi.PaymentContext context) {
            return null;
        }
    }
}
