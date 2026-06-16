package io.labs64.paymentgateway.mapper;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import io.labs64.paymentgateway.entity.PaymentEntity;
import io.labs64.paymentgateway.entity.PaymentTransactionEntity;
import io.labs64.paymentgateway.model.PaymentTransaction;
import io.labs64.paymentgateway.model.PaymentTransactionStatus;
import io.labs64.paymentgateway.model.PaymentTransactionsResponse;
import io.labs64.paymentgateway.model.StatusDetails;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentTransactionMapperTest {

    private final PaymentTransactionMapper mapper = new PaymentTransactionMapperImpl();

    @Test
    void toDtoMapsPaymentIdStatusDetailsAndPspData() {
        final PaymentTransactionEntity entity = transaction();

        final PaymentTransaction dto = mapper.toDto(entity);

        assertThat(dto.getId()).isEqualTo(entity.getId());
        assertThat(dto.getPaymentId()).isEqualTo(entity.getPayment().getId());
        assertThat(dto.getStatus()).isEqualTo(PaymentTransactionStatus.FAILED);
        assertThat(dto.getStatusDetails()).isEqualTo(new StatusDetails("CARD_EXPIRED", "Card expired"));
        assertThat(dto.getPspData()).containsEntry("providerReference", "pi_123");
        assertThat(dto.getCreatedAt()).isEqualTo(entity.getCreatedAt());
        assertThat(dto.getUpdatedAt()).isEqualTo(entity.getUpdatedAt());
    }

    @Test
    void toDtoReturnsNullPaymentIdWhenPaymentIsMissing() {
        final PaymentTransactionEntity entity = transaction();
        entity.setPayment(null);

        final PaymentTransaction dto = mapper.toDto(entity);

        assertThat(dto.getPaymentId()).isNull();
    }

    @Test
    void toPaymentTransactionsResponseMapsPageContent() {
        final PaymentTransactionEntity entity = transaction();

        final PaymentTransactionsResponse response = mapper.toPaymentTransactionsResponse(
                new PageImpl<>(List.of(entity), PageRequest.of(1, 20), 21));

        assertThat(response.getItems()).hasSize(1);
        assertThat(response.getItems().get(0).getId()).isEqualTo(entity.getId());
        assertThat(response.getPage()).isEqualTo(1);
        assertThat(response.getPageSize()).isEqualTo(20);
        assertThat(response.getTotalItems()).isEqualTo(21);
        assertThat(response.getTotalPages()).isEqualTo(2);
        assertThat(response.getHasPrev()).isTrue();
        assertThat(response.getHasNext()).isFalse();
    }

    @Test
    void toPaymentTransactionsResponseMapsIterable() {
        final PaymentTransactionEntity entity = transaction();

        final PaymentTransactionsResponse response = mapper.toPaymentTransactionsResponse(List.of(entity));

        assertThat(response.getItems()).hasSize(1);
        assertThat(response.getItems().get(0).getPaymentId()).isEqualTo(entity.getPayment().getId());
    }

    private static PaymentTransactionEntity transaction() {
        final OffsetDateTime now = OffsetDateTime.now();
        return PaymentTransactionEntity.builder()
                .id(UUID.randomUUID())
                .tenantId("tenant-a")
                .payment(PaymentEntity.builder().id(UUID.randomUUID()).build())
                .status(PaymentTransactionStatus.FAILED)
                .statusDetails(new StatusDetails("CARD_EXPIRED", "Card expired"))
                .pspData(Map.of("providerReference", "pi_123"))
                .createdAt(now.minusMinutes(1))
                .updatedAt(now)
                .build();
    }
}
