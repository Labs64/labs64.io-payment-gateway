package io.labs64.paymentgateway.idempotency;

import java.time.OffsetDateTime;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.labs64.paymentgateway.service.IdempotencyService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class IdempotencyResponseBodyAdviceTest {

    private final IdempotencyService service = mock(IdempotencyService.class);
    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @Test
    void completesIdempotencyRecordWhenRequestHasContext() {
        final MockHttpServletRequest servletRequest = new MockHttpServletRequest();
        final MockHttpServletResponse servletResponse = new MockHttpServletResponse();
        servletResponse.setStatus(201);
        final IdempotencyContext context = new IdempotencyContext(
                "tenant-a",
                "idk-1",
                "hash-1",
                new IdempotencyOperation("POST", "/api/v1/payments/{paymentId}/pay"));
        servletRequest.setAttribute(IdempotencyInterceptor.REQUEST_CONTEXT_ATTRIBUTE, context);
        final IdempotencyResponseBodyAdvice advice = new IdempotencyResponseBodyAdvice(service, servletRequest, objectMapper);

        final Object body = Map.of("id", "payment-1");
        final Object result = advice.beforeBodyWrite(
                body,
                mock(MethodParameter.class),
                MediaType.APPLICATION_JSON,
                JacksonJsonHttpMessageConverter.class,
                new ServletServerHttpRequest(servletRequest),
                new ServletServerHttpResponse(servletResponse));

        final ArgumentCaptor<IdempotencyResponse> responseCaptor = ArgumentCaptor.forClass(IdempotencyResponse.class);
        verify(service).complete(org.mockito.ArgumentMatchers.eq(context), responseCaptor.capture());
        assertThat(result).isSameAs(body);
        assertThat(responseCaptor.getValue().status()).isEqualTo(201);
        assertThat(responseCaptor.getValue().body()).isEqualTo(body);
    }

    @Test
    void storesOffsetDateTimeAsIsoString() {
        final MockHttpServletRequest servletRequest = new MockHttpServletRequest();
        final MockHttpServletResponse servletResponse = new MockHttpServletResponse();
        final IdempotencyContext context = new IdempotencyContext(
                "tenant-a",
                "idk-1",
                "hash-1",
                new IdempotencyOperation("POST", "/api/v1/payments/{paymentId}/pay"));
        servletRequest.setAttribute(IdempotencyInterceptor.REQUEST_CONTEXT_ATTRIBUTE, context);
        final IdempotencyResponseBodyAdvice advice = new IdempotencyResponseBodyAdvice(service, servletRequest, objectMapper);

        final Object body = Map.of("createdAt", OffsetDateTime.parse("2026-06-26T10:20:30Z"));
        advice.beforeBodyWrite(
                body,
                mock(MethodParameter.class),
                MediaType.APPLICATION_JSON,
                JacksonJsonHttpMessageConverter.class,
                new ServletServerHttpRequest(servletRequest),
                new ServletServerHttpResponse(servletResponse));

        final ArgumentCaptor<IdempotencyResponse> responseCaptor = ArgumentCaptor.forClass(IdempotencyResponse.class);
        verify(service).complete(org.mockito.ArgumentMatchers.eq(context), responseCaptor.capture());
        assertThat(responseCaptor.getValue().body())
                .isEqualTo(Map.of("createdAt", "2026-06-26T10:20:30Z"));
    }

    @Test
    void doesNothingWhenRequestHasNoContext() {
        final MockHttpServletRequest servletRequest = new MockHttpServletRequest();
        final IdempotencyResponseBodyAdvice advice = new IdempotencyResponseBodyAdvice(service, servletRequest, objectMapper);

        final Object body = Map.of("id", "payment-1");
        final Object result = advice.beforeBodyWrite(
                body,
                mock(MethodParameter.class),
                MediaType.APPLICATION_JSON,
                JacksonJsonHttpMessageConverter.class,
                new ServletServerHttpRequest(servletRequest),
                new ServletServerHttpResponse(new MockHttpServletResponse()));

        assertThat(result).isSameAs(body);
        verify(service, never()).complete(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }
}
