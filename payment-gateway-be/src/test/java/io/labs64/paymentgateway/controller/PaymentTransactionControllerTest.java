package io.labs64.paymentgateway.controller;

import java.util.List;
import java.util.UUID;

import io.labs64.paymentgateway.entity.PaymentTransactionEntity;
import io.labs64.paymentgateway.mapper.PaymentTransactionMapper;
import io.labs64.paymentgateway.model.PaymentTransaction;
import io.labs64.paymentgateway.model.PaymentTransactionStatus;
import io.labs64.paymentgateway.model.PaymentTransactionsResponse;
import io.labs64.paymentgateway.security.AuthPrincipal;
import io.labs64.paymentgateway.security.Scopes;
import io.labs64.paymentgateway.service.PaymentTransactionService;
import io.labs64.paymentgateway.service.filter.PaymentTransactionFilter;
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
class PaymentTransactionControllerTest {

    private static final String TENANT_ID = "tenant-a";

    @Mock
    private PaymentTransactionService service;

    @Mock
    private PaymentTransactionMapper mapper;

    @InjectMocks
    private PaymentTransactionController controller;

    @BeforeEach
    void setUp() {
        authenticate();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void listPaymentTransactionsBuildsFilterAndMapsResponse() {
        final UUID paymentId = UUID.randomUUID();
        final PageImpl<PaymentTransactionEntity> page = new PageImpl<>(List.of(transaction()));
        final PaymentTransactionsResponse response = new PaymentTransactionsResponse();

        when(service.list(eq(TENANT_ID), any(PaymentTransactionFilter.class), eq(PageRequest.of(1, 20))))
                .thenReturn(page);
        when(mapper.toPaymentTransactionsResponse(page)).thenReturn(response);

        final ResponseEntity<PaymentTransactionsResponse> result = controller.listPaymentTransactions(
                paymentId,
                PaymentTransactionStatus.FAILED,
                PageRequest.of(1, 20));

        final ArgumentCaptor<PaymentTransactionFilter> filterCaptor = ArgumentCaptor.forClass(PaymentTransactionFilter.class);
        verify(service).list(eq(TENANT_ID), filterCaptor.capture(), eq(PageRequest.of(1, 20)));
        assertThat(filterCaptor.getValue().paymentId()).isEqualTo(paymentId);
        assertThat(filterCaptor.getValue().status()).isEqualTo(PaymentTransactionStatus.FAILED);
        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isSameAs(response);
    }

    @Test
    void getPaymentTransactionUsesTenantScopedServiceAndMapsDto() {
        final UUID transactionId = UUID.randomUUID();
        final PaymentTransactionEntity entity = transaction();
        final PaymentTransaction dto = new PaymentTransaction();

        when(service.get(TENANT_ID, transactionId)).thenReturn(entity);
        when(mapper.toDto(entity)).thenReturn(dto);

        final ResponseEntity<PaymentTransaction> result = controller.getPaymentTransaction(transactionId);

        verify(service).get(TENANT_ID, transactionId);
        verify(mapper).toDto(entity);
        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isSameAs(dto);
    }

    private static void authenticate() {
        final TestingAuthenticationToken authentication = new TestingAuthenticationToken(
                new AuthPrincipal(TENANT_ID),
                "n/a",
                List.of(new SimpleGrantedAuthority("SCOPE_" + Scopes.PAYMENT_TRANSACTION_READ)));
        authentication.setAuthenticated(true);
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    private static PaymentTransactionEntity transaction() {
        return PaymentTransactionEntity.builder()
                .id(UUID.randomUUID())
                .tenantId(TENANT_ID)
                .status(PaymentTransactionStatus.PENDING)
                .build();
    }
}
