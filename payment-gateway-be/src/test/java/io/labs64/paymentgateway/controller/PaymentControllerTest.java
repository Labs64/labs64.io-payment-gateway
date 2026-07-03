package io.labs64.paymentgateway.controller;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import io.labs64.paymentgateway.entity.PaymentEntity;
import io.labs64.paymentgateway.entity.PaymentProviderEntity;
import io.labs64.paymentgateway.entity.PaymentTransactionEntity;
import io.labs64.paymentgateway.mapper.PaymentMapper;
import io.labs64.paymentgateway.mapper.PaymentTransactionMapper;
import io.labs64.paymentgateway.model.BillingInfo;
import io.labs64.paymentgateway.model.CreatePaymentRequest;
import io.labs64.paymentgateway.model.ExecutePaymentResponse;
import io.labs64.paymentgateway.model.NextAction;
import io.labs64.paymentgateway.model.OrderItem;
import io.labs64.paymentgateway.model.Payment;
import io.labs64.paymentgateway.model.PaymentListResponse;
import io.labs64.paymentgateway.model.PaymentStatus;
import io.labs64.paymentgateway.model.PaymentTransaction;
import io.labs64.paymentgateway.model.PaymentTransactionStatus;
import io.labs64.paymentgateway.model.PurchaseOrder;
import io.labs64.paymentgateway.psp.spi.PaymentExecutionRequest;
import io.labs64.paymentgateway.psp.spi.PaymentNextAction;
import io.labs64.paymentgateway.security.AuthPrincipal;
import io.labs64.paymentgateway.security.Scopes;
import io.labs64.paymentgateway.service.PayPaymentResponse;
import io.labs64.paymentgateway.service.PaymentService;
import io.labs64.paymentgateway.service.filter.PaymentFilter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentControllerTest {

    private static final String TENANT_ID = "tenant-a";
    private static final String PROVIDER = "stripe";

    @Mock
    private PaymentService service;

    @Mock
    private PaymentMapper paymentMapper;

    @Mock
    private PaymentTransactionMapper paymentTransactionMapper;

    @InjectMocks
    private PaymentController controller;

    @BeforeEach
    void setUp() {
        authenticate();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void createPaymentPassesProviderSeparatelyFromMappedEntity() {
        final CreatePaymentRequest request = createRequest();
        final PaymentEntity mapped = PaymentEntity.builder().purchaseOrder(Map.of("grossAmount", 3000L)).build();
        final PaymentEntity saved = payment();
        final Payment dto = new Payment();

        when(paymentMapper.toEntity(request)).thenReturn(mapped);
        when(service.create(TENANT_ID, PROVIDER, mapped)).thenReturn(saved);
        when(paymentMapper.toDto(saved)).thenReturn(dto);

        final ResponseEntity<Payment> result = controller.createPayment(request);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(result.getBody()).isSameAs(dto);
        verify(service).create(TENANT_ID, PROVIDER, mapped);
    }

    @Test
    void listPaymentsBuildsStatusFilter() {
        final PageImpl<PaymentEntity> page = new PageImpl<>(List.of(payment()));
        final PaymentListResponse response = new PaymentListResponse();
        when(service.list(eq(TENANT_ID), any(PaymentFilter.class), eq(PageRequest.of(2, 25)))).thenReturn(page);
        when(paymentMapper.toPage(page)).thenReturn(response);

        final ResponseEntity<PaymentListResponse> result = controller.listPayments(
                PaymentStatus.READY,
                PageRequest.of(2, 25));

        final ArgumentCaptor<PaymentFilter> filterCaptor = ArgumentCaptor.forClass(PaymentFilter.class);
        verify(service).list(eq(TENANT_ID), filterCaptor.capture(), eq(PageRequest.of(2, 25)));
        assertThat(filterCaptor.getValue().status()).isEqualTo(PaymentStatus.READY);
        assertThat(result.getBody()).isSameAs(response);
    }

    @Test
    void payPaymentReturnsPaymentTransactionAndNextAction() {
        final UUID paymentId = UUID.randomUUID();
        final PaymentEntity payment = payment();
        final PaymentTransactionEntity transaction = transaction(payment);
        final Payment paymentDto = new Payment();
        final PaymentTransaction transactionDto = new PaymentTransaction();
        final PaymentNextAction nextAction = new PaymentNextAction(
                NextAction.TypeEnum.REDIRECT,
                Map.of("url", "https://psp.example/redirect"));

        when(service.pay(TENANT_ID, paymentId, PaymentExecutionRequest.empty()))
                .thenReturn(new PayPaymentResponse(payment, transaction, nextAction));
        when(paymentMapper.toDto(payment)).thenReturn(paymentDto);
        when(paymentTransactionMapper.toDto(transaction)).thenReturn(transactionDto);

        final ResponseEntity<ExecutePaymentResponse> result = controller.payPayment(paymentId, null);
        final ExecutePaymentResponse body = Objects.requireNonNull(result.getBody());
        final NextAction responseNextAction = Objects.requireNonNull(body.getNextAction());

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(body.getPayment()).isSameAs(paymentDto);
        assertThat(body.getPaymentTransaction()).isSameAs(transactionDto);
        assertThat(responseNextAction.getType()).isEqualTo(NextAction.TypeEnum.REDIRECT);
        assertThat(responseNextAction.getDetails()).containsEntry("url", "https://psp.example/redirect");
    }

    @Test
    void closePaymentDelegatesToServiceClose() {
        final UUID paymentId = UUID.randomUUID();
        final PaymentEntity payment = payment();
        final Payment dto = new Payment();
        payment.setStatus(PaymentStatus.CLOSED);
        when(service.close(TENANT_ID, paymentId)).thenReturn(payment);
        when(paymentMapper.toDto(payment)).thenReturn(dto);

        final ResponseEntity<Payment> result = controller.closePayment(paymentId);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isSameAs(dto);
        verify(service).close(TENANT_ID, paymentId);
    }

    private static void authenticate() {
        final TestingAuthenticationToken authentication = new TestingAuthenticationToken(
                new AuthPrincipal(TENANT_ID),
                "n/a",
                List.of(
                        new SimpleGrantedAuthority("SCOPE_" + Scopes.PAYMENT_WRITE),
                        new SimpleGrantedAuthority("SCOPE_" + Scopes.PAYMENT_PAY)));
        authentication.setAuthenticated(true);
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    private static CreatePaymentRequest createRequest() {
        return new CreatePaymentRequest(
                PROVIDER,
                new PurchaseOrder("USD", List.of(new OrderItem("Widget", 3000L, 1)), 3000L),
                new BillingInfo("customer@example.com"));
    }

    private static PaymentEntity payment() {
        return PaymentEntity.builder()
                .id(UUID.randomUUID())
                .tenantId(TENANT_ID)
                .paymentProvider(PaymentProviderEntity.builder().provider(PROVIDER).build())
                .status(PaymentStatus.READY)
                .purchaseOrder(Map.of("grossAmount", 3000L, "currency", "USD"))
                .build();
    }

    private static PaymentTransactionEntity transaction(final PaymentEntity payment) {
        return PaymentTransactionEntity.builder()
                .id(UUID.randomUUID())
                .tenantId(TENANT_ID)
                .payment(payment)
                .status(PaymentTransactionStatus.SUCCESS)
                .build();
    }
}
