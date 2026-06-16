package io.labs64.paymentgateway.idempotency;

import java.util.Map;

import io.labs64.paymentgateway.service.IdempotencyService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
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
        final IdempotencyResponseBodyAdvice advice = new IdempotencyResponseBodyAdvice(service, servletRequest);

        final Object body = Map.of("id", "payment-1");
        final Object result = advice.beforeBodyWrite(
                body,
                mock(MethodParameter.class),
                MediaType.APPLICATION_JSON,
                MappingJackson2HttpMessageConverter.class,
                new ServletServerHttpRequest(servletRequest),
                new ServletServerHttpResponse(servletResponse));

        final ArgumentCaptor<IdempotencyResponse> responseCaptor = ArgumentCaptor.forClass(IdempotencyResponse.class);
        verify(service).complete(org.mockito.ArgumentMatchers.eq(context), responseCaptor.capture());
        assertThat(result).isSameAs(body);
        assertThat(responseCaptor.getValue().status()).isEqualTo(201);
        assertThat(responseCaptor.getValue().body()).isSameAs(body);
    }

    @Test
    void doesNothingWhenRequestHasNoContext() {
        final MockHttpServletRequest servletRequest = new MockHttpServletRequest();
        final IdempotencyResponseBodyAdvice advice = new IdempotencyResponseBodyAdvice(service, servletRequest);

        final Object body = Map.of("id", "payment-1");
        final Object result = advice.beforeBodyWrite(
                body,
                mock(MethodParameter.class),
                MediaType.APPLICATION_JSON,
                MappingJackson2HttpMessageConverter.class,
                new ServletServerHttpRequest(servletRequest),
                new ServletServerHttpResponse(new MockHttpServletResponse()));

        assertThat(result).isSameAs(body);
        verify(service, never()).complete(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }
}
