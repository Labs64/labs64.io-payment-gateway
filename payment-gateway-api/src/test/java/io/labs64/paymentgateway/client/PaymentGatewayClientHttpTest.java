package io.labs64.paymentgateway.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.labs64.paymentgateway.model.ErrorCode;
import io.labs64.paymentgateway.model.PaymentProvider;
import io.labs64.paymentgateway.model.PaymentProviderCreateRequest;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PaymentGatewayClientHttpTest {

    private static final String API_PATH = "/payment-gateway/api/v1";
    private static final String PROVIDERS_PATH = API_PATH + "/payment-providers";

    private final AtomicReference<PreparedResponse> preparedResponse = new AtomicReference<>();
    private final List<CapturedRequest> requests = new CopyOnWriteArrayList<>();

    private HttpServer server;
    private URI baseUrl;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext(API_PATH, this::handle);
        server.start();
        baseUrl = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + API_PATH);
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void listsPaymentProvidersWithHeadersQueryAndResponsePagination() {
        preparedResponse.set(jsonResponse(200, pagedProvidersJson(), "response-correlation"));
        final AtomicInteger tokenCalls = new AtomicInteger();
        final PaymentGatewayClient client = PaymentGatewayClient.builder()
                .baseUrl(baseUrl)
                .accessTokenProvider(() -> "token-" + tokenCalls.incrementAndGet())
                .correlationProvider(() -> Optional.of("provider-correlation"))
                .build();
        final PaymentProviderQuery query = PaymentProviderQuery.builder()
                .currency("eur")
                .country("de")
                .active(true)
                .page(2)
                .pageSize(20)
                .sort("createdAt,desc")
                .sort("name,asc")
                .build();
        final CallOptions options = CallOptions.builder()
                .correlationId("call-correlation")
                .idempotencyKey("list-request-1")
                .headers(Map.of(
                        "Authorization", "Custom value",
                        "X-Auth-User", "svc:auditflow"))
                .build();

        final PagedResult<PaymentProvider> result = client.paymentProviders().list(query, options);

        assertEquals(1, result.size());
        assertEquals("Stripe Europe", result.items().get(0).getName());
        assertEquals(new PageInfo(2, 20, 125, 7, true, true), result.pageInfo());

        final CapturedRequest first = requests.get(0);
        assertEquals("GET", first.method());
        assertEquals(PROVIDERS_PATH, first.uri().getPath());
        assertEquals("Bearer token-1", first.authorization());
        assertEquals("call-correlation", first.correlationId());
        assertEquals("list-request-1", first.idempotencyKey());
        assertEquals("svc:auditflow", first.internalUser());
        assertEquals(Map.of(
                "currency", List.of("EUR"),
                "country", List.of("DE"),
                "active", List.of("true"),
                "page", List.of("2"),
                "size", List.of("20"),
                "sort", List.of("createdAt,desc", "name,asc")), queryValues(first.uri()));

        client.paymentProviders().list(query);

        assertEquals(2, tokenCalls.get());
        assertEquals("Bearer token-2", requests.get(1).authorization());
        assertEquals("provider-correlation", requests.get(1).correlationId());
    }

    @Test
    void serializesCreateRequestAndAppliesIdempotencyHeader() {
        preparedResponse.set(jsonResponse(200, providerJson(), null));
        final PaymentGatewayClient client = PaymentGatewayClient.builder()
                .baseUrl(baseUrl)
                .bearerToken("fixed-token")
                .build();
        final PaymentProviderCreateRequest request = new PaymentProviderCreateRequest()
                .provider("stripe")
                .active(true)
                .config(Map.of("merchantId", "merchant-1"));
        final CallOptions options = CallOptions.builder()
                .idempotencyKey("provider-create-1")
                .build();

        final PaymentProvider result = client.paymentProviders().create(request, options);

        assertEquals("stripe", result.getProvider());
        final CapturedRequest captured = requests.get(0);
        assertEquals("POST", captured.method());
        assertEquals("Bearer fixed-token", captured.authorization());
        assertEquals("provider-create-1", captured.idempotencyKey());
        assertTrue(captured.body().contains("\"provider\":\"stripe\""));
        assertTrue(captured.body().contains("\"active\":true"));
        assertTrue(captured.body().contains("\"merchantId\":\"merchant-1\""));
    }

    @Test
    void exposesStructuredValidationError() {
        preparedResponse.set(jsonResponse(400, """
                {
                  "code": "VALIDATION_ERROR",
                  "message": "currency must contain three letters",
                  "timestamp": "2026-08-06T12:00:00Z",
                  "traceId": "trace-123"
                }
                """, "response-correlation"));
        final PaymentGatewayClient client = PaymentGatewayClient.builder().baseUrl(baseUrl).build();

        final PaymentGatewayApiException exception = assertThrows(
                PaymentGatewayApiException.class,
                () -> client.paymentProviders().list());

        assertEquals(400, exception.statusCode());
        assertEquals(ErrorCode.VALIDATION_ERROR, exception.error().getCode());
        assertEquals("currency must contain three letters", exception.error().getMessage());
        assertEquals("trace-123", exception.error().getTraceId());
        assertEquals("response-correlation", exception.correlationId());
    }

    @Test
    void mapsUnauthorizedResponseToAuthenticationException() {
        preparedResponse.set(jsonResponse(401, """
                {
                  "code": "UNAUTHORIZED",
                  "message": "Access token is invalid",
                  "timestamp": "2026-08-06T12:00:00Z"
                }
                """, null));
        final PaymentGatewayClient client = PaymentGatewayClient.builder().baseUrl(baseUrl).build();

        final PaymentGatewayApiException exception = assertThrows(
                PaymentGatewayApiException.class,
                () -> client.paymentProviders().list());

        assertInstanceOf(PaymentGatewayAuthenticationException.class, exception);
        assertEquals(401, exception.statusCode());
        assertEquals(ErrorCode.UNAUTHORIZED, exception.error().getCode());
    }

    @Test
    void preservesStatusWhenErrorBodyIsNotStructuredJson() {
        preparedResponse.set(jsonResponse(502, "upstream unavailable", null));
        final PaymentGatewayClient client = PaymentGatewayClient.builder().baseUrl(baseUrl).build();

        final PaymentGatewayApiException exception = assertThrows(
                PaymentGatewayApiException.class,
                () -> client.paymentProviders().list());

        assertEquals(502, exception.statusCode());
        assertNull(exception.error());
        assertFalse(exception.headers().map().isEmpty());
    }

    private void handle(final HttpExchange exchange) throws IOException {
        final byte[] requestBody = exchange.getRequestBody().readAllBytes();
        requests.add(new CapturedRequest(
                exchange.getRequestMethod(),
                exchange.getRequestURI(),
                exchange.getRequestHeaders().getFirst("Authorization"),
                exchange.getRequestHeaders().getFirst("X-Correlation-ID"),
                exchange.getRequestHeaders().getFirst("Idempotency-Key"),
                exchange.getRequestHeaders().getFirst("X-Auth-User"),
                new String(requestBody, StandardCharsets.UTF_8)));

        final PreparedResponse response = preparedResponse.get();
        if (response == null) {
            throw new IllegalStateException("Test response was not configured");
        }
        response.headers().forEach((name, value) -> exchange.getResponseHeaders().add(name, value));
        final byte[] body = response.body().getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(response.statusCode(), body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
    }

    private static PreparedResponse jsonResponse(
            final int statusCode, final String body, final String correlationId) {
        final Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Content-Type", "application/json");
        if (correlationId != null) {
            headers.put("X-Correlation-ID", correlationId);
        }
        return new PreparedResponse(statusCode, body, headers);
    }

    private static Map<String, List<String>> queryValues(final URI uri) {
        final Map<String, List<String>> values = new LinkedHashMap<>();
        for (String pair : uri.getRawQuery().split("&")) {
            final String[] parts = pair.split("=", 2);
            final String name = URLDecoder.decode(parts[0], StandardCharsets.UTF_8);
            final String value = URLDecoder.decode(parts[1], StandardCharsets.UTF_8);
            values.computeIfAbsent(name, ignored -> new ArrayList<>()).add(value);
        }
        return values;
    }

    private static String pagedProvidersJson() {
        return """
                {
                  "page": 2,
                  "pageSize": 20,
                  "totalItems": 125,
                  "totalPages": 7,
                  "hasPrev": true,
                  "hasNext": true,
                  "items": [
                    {
                      "id": "550e8400-e29b-41d4-a716-446655440010",
                      "provider": "stripe",
                      "name": "Stripe Europe",
                      "description": "Card payments",
                      "recurring": true,
                      "active": true
                    }
                  ]
                }
                """;
    }

    private static String providerJson() {
        return """
                {
                  "id": "550e8400-e29b-41d4-a716-446655440010",
                  "provider": "stripe",
                  "name": "Stripe Europe",
                  "description": "Card payments",
                  "recurring": true,
                  "active": true
                }
                """;
    }

    private record PreparedResponse(int statusCode, String body, Map<String, String> headers) {
    }

    private record CapturedRequest(
            String method,
            URI uri,
            String authorization,
            String correlationId,
            String idempotencyKey,
            String internalUser,
            String body) {
    }
}
