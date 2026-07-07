package io.labs64.paymentgateway.idempotency;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Optional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.labs64.paymentgateway.service.IdempotencyService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import io.labs64.authcontext.UserContext;
import io.labs64.authcontext.UserContextHolder;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerMapping;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IdempotencyInterceptorTest {

    private final IdempotencyService service = mock(IdempotencyService.class);
    private final IdempotencyInterceptor interceptor = new IdempotencyInterceptor(service, objectMapper());

    @AfterEach
    void tearDown() {
        UserContextHolder.clear();
    }

    @Test
    void skipsNonIdempotentHandlerMethods() throws Exception {
        authenticate();
        final MockHttpServletRequest request = request();
        request.addHeader(IdempotencyInterceptor.IDEMPOTENCY_KEY_HEADER, "idk-1");

        final boolean result = interceptor.preHandle(
                request,
                new MockHttpServletResponse(),
                new HandlerMethod(new Handler(), "nonIdempotent"));

        assertThat(result).isTrue();
        verify(service, never()).startOrReplay(any());
    }

    @Test
    void skipsWhenIdempotencyKeyIsMissing() throws Exception {
        authenticate();

        final boolean result = interceptor.preHandle(
                request(),
                new MockHttpServletResponse(),
                new HandlerMethod(new Handler(), "idempotent"));

        assertThat(result).isTrue();
        verify(service, never()).startOrReplay(any());
    }

    @Test
    void storesContextAndContinuesWhenNoCachedResponseExists() throws Exception {
        authenticate();
        final MockHttpServletRequest request = request();
        request.addHeader(IdempotencyInterceptor.IDEMPOTENCY_KEY_HEADER, "idk-1");
        request.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, "/api/v1/payments/{paymentId}/pay");
        when(service.startOrReplay(any())).thenReturn(Optional.empty());

        final boolean result = interceptor.preHandle(
                request,
                new MockHttpServletResponse(),
                new HandlerMethod(new Handler(), "idempotent"));

        assertThat(result).isTrue();
        assertThat(request.getAttribute(IdempotencyInterceptor.REQUEST_CONTEXT_ATTRIBUTE))
                .isInstanceOf(IdempotencyContext.class)
                .extracting("tenantId", "idempotencyKey")
                .containsExactly("tenant-a", "idk-1");
    }

    @Test
    void writesCachedResponseAndStopsHandlerExecution() throws Exception {
        authenticate();
        final MockHttpServletRequest request = request();
        request.addHeader(IdempotencyInterceptor.IDEMPOTENCY_KEY_HEADER, "idk-1");
        final MockHttpServletResponse response = new MockHttpServletResponse();
        final HttpHeaders headers = new HttpHeaders();
        headers.add("Location", "/payments/123");
        when(service.startOrReplay(any())).thenReturn(Optional.of(new IdempotencyResponse(
                201,
                headers,
                Map.of(
                        "id", "payment-1",
                        "createdAt", OffsetDateTime.parse("2026-06-16T12:32:49Z")))));

        final boolean result = interceptor.preHandle(
                request,
                response,
                new HandlerMethod(new Handler(), "idempotent"));

        assertThat(result).isFalse();
        assertThat(response.getStatus()).isEqualTo(201);
        assertThat(response.getHeader("Location")).isEqualTo("/payments/123");
        assertThat(response.getContentAsString()).contains("payment-1");
        assertThat(response.getContentAsString()).contains("2026-06-16T12:32:49Z");
    }

    private static MockHttpServletRequest request() {
        return new MockHttpServletRequest("POST", "/api/v1/payments/123/pay");
    }

    private static void authenticate() {
        UserContextHolder.set(new UserContext("test-user", "tenant-a",
                java.util.Set.of("ecommerce-role"), "test-request-id"));
    }

    private static ObjectMapper objectMapper() {
        return new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    @SuppressWarnings("unused")
    private static class Handler {
        @IdempotentOperation
        public void idempotent() {
        }

        public void nonIdempotent() {
        }
    }
}
