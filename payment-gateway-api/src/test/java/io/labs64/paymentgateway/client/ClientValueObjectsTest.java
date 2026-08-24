package io.labs64.paymentgateway.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ClientValueObjectsTest {

    @Test
    void callOptionsAreImmutableAndCanBeCopied() {
        final CallOptions original = CallOptions.builder()
                .correlationId("correlation-1")
                .idempotencyKey("payment-1")
                .timeout(Duration.ofSeconds(10))
                .header("X-Auth-User", "svc:auditflow")
                .build();

        final CallOptions changed = original.toBuilder()
                .correlationId("correlation-2")
                .build();

        assertEquals("correlation-1", original.correlationId());
        assertEquals("correlation-2", changed.correlationId());
        assertEquals("payment-1", changed.idempotencyKey());
        assertEquals(Duration.ofSeconds(10), changed.timeout());
        assertEquals(Map.of("X-Auth-User", "svc:auditflow"), changed.headers());
    }

    @Test
    void callOptionsRejectInvalidValues() {
        assertThrows(IllegalArgumentException.class,
                () -> CallOptions.builder().correlationId("  ").build());
        assertThrows(IllegalArgumentException.class,
                () -> CallOptions.builder().idempotencyKey("").build());
        assertThrows(IllegalArgumentException.class,
                () -> CallOptions.builder().timeout(Duration.ZERO).build());
    }

    @Test
    void paymentProviderQueryNormalizesCodesAndKeepsPagination() {
        final PaymentProviderQuery query = PaymentProviderQuery.builder()
                .currency(" eur ")
                .country("de")
                .active(true)
                .page(2)
                .pageSize(20)
                .sort("createdAt,desc")
                .sort("name,asc")
                .build();

        assertEquals("EUR", query.currency());
        assertEquals("DE", query.country());
        assertTrue(query.active());
        assertEquals(2, query.page());
        assertEquals(20, query.pageSize());
        assertEquals(List.of("createdAt,desc", "name,asc"), query.sort());
    }

    @Test
    void paymentProviderQueryRejectsInvalidPagination() {
        assertThrows(IllegalArgumentException.class,
                () -> PaymentProviderQuery.builder().page(-1).build());
        assertThrows(IllegalArgumentException.class,
                () -> PaymentProviderQuery.builder().pageSize(0).build());
        assertThrows(IllegalArgumentException.class,
                () -> PaymentProviderQuery.builder().currency("EU").build());
        assertThrows(IllegalArgumentException.class,
                () -> PaymentProviderQuery.builder().sort(" ").build());
    }

    @Test
    void pagedResultDefensivelyCopiesItemsAndExposesCollectionHelpers() {
        final List<String> source = new ArrayList<>(List.of("one", "two"));
        final PageInfo pageInfo = new PageInfo(1, 2, 5, 3, true, true);
        final PagedResult<String> result = new PagedResult<>(source, pageInfo);
        source.add("three");

        assertEquals(List.of("one", "two"), result.items());
        assertEquals(pageInfo, result.pageInfo());
        assertEquals(2, result.size());
        assertFalse(result.isEmpty());
        assertEquals(List.of("ONE", "TWO"), result.stream().map(String::toUpperCase).toList());
        assertThrows(UnsupportedOperationException.class, () -> result.items().add("three"));
    }
}
