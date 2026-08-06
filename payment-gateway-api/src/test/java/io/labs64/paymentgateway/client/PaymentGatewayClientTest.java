package io.labs64.paymentgateway.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class PaymentGatewayClientTest {

    @Test
    void normalizesTrailingSlashInBaseUrl() {
        final PaymentGatewayClient client = PaymentGatewayClient.builder()
                .baseUrl(URI.create("https://gateway.example.com/payment-gateway/api/v1/"))
                .build();

        assertEquals(
                URI.create("https://gateway.example.com/payment-gateway/api/v1"),
                client.baseUrl());
        assertSame(client.paymentProviders(), client.paymentProviders());
        assertSame(client.payments(), client.payments());
    }

    @Test
    void requiresAnAbsoluteHttpBaseUrl() {
        assertThrows(NullPointerException.class, () -> PaymentGatewayClient.builder().build());
        assertThrows(IllegalArgumentException.class, () -> PaymentGatewayClient.builder()
                .baseUrl(URI.create("/payment-gateway/api/v1"))
                .build());
        assertThrows(IllegalArgumentException.class, () -> PaymentGatewayClient.builder()
                .baseUrl(URI.create("ftp://gateway.example.com/payment-gateway/api/v1"))
                .build());
        assertThrows(IllegalArgumentException.class, () -> PaymentGatewayClient.builder()
                .baseUrl(URI.create("https://gateway.example.com/api/v1?tenant=one"))
                .build());
    }

    @Test
    void rejectsCompetingAuthenticationSources() {
        assertThrows(IllegalStateException.class, () -> PaymentGatewayClient.builder()
                .baseUrl(URI.create("https://gateway.example.com/api/v1"))
                .bearerToken("fixed-token")
                .accessTokenProvider(() -> "dynamic-token")
                .build());
    }

    @Test
    void validatesTimeoutConfiguration() {
        assertThrows(IllegalArgumentException.class, () -> PaymentGatewayClient.builder()
                .baseUrl(URI.create("https://gateway.example.com/api/v1"))
                .connectTimeout(Duration.ZERO)
                .build());
        assertThrows(IllegalArgumentException.class, () -> PaymentGatewayClient.builder()
                .baseUrl(URI.create("https://gateway.example.com/api/v1"))
                .callTimeout(Duration.ofSeconds(-1))
                .build());
    }

    @Test
    void customHttpClientOwnsItsConnectTimeout() {
        assertThrows(IllegalStateException.class, () -> PaymentGatewayClient.builder()
                .baseUrl(URI.create("https://gateway.example.com/api/v1"))
                .httpClient(HttpClient.newHttpClient())
                .connectTimeout(Duration.ofSeconds(2))
                .build());
    }
}
