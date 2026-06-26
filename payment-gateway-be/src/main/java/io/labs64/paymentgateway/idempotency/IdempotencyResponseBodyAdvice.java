package io.labs64.paymentgateway.idempotency;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.labs64.paymentgateway.service.IdempotencyService;
import org.jetbrains.annotations.NotNull;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

@RestControllerAdvice
@RequiredArgsConstructor
public class IdempotencyResponseBodyAdvice implements ResponseBodyAdvice<Object> {

    private final IdempotencyService idempotencyService;
    private final HttpServletRequest request;
    private final ObjectMapper objectMapper;

    @Override
    public boolean supports(
            @NotNull final MethodParameter returnType,
            @NotNull final Class<? extends HttpMessageConverter<?>> converterType) {
        return true;
    }

    @Override
    public Object beforeBodyWrite(
            final Object body,
            @NotNull final MethodParameter returnType,
            @NotNull final MediaType selectedContentType,
            @NotNull final Class<? extends HttpMessageConverter<?>> selectedConverterType,
            @NotNull final ServerHttpRequest serverHttpRequest,
            @NotNull final ServerHttpResponse serverHttpResponse) {
        final Object context = request.getAttribute(IdempotencyInterceptor.REQUEST_CONTEXT_ATTRIBUTE);
        if (context instanceof IdempotencyContext idempotencyContext) {
            idempotencyService.complete(idempotencyContext, new IdempotencyResponse(
                    status(serverHttpResponse),
                    HttpHeaders.copyOf(serverHttpResponse.getHeaders()),
                    toJsonCompatibleBody(body)));
        }
        return body;
    }

    private Object toJsonCompatibleBody(final Object body) {
        return body != null ? objectMapper.convertValue(body, Object.class) : null;
    }

    private int status(final ServerHttpResponse response) {
        if (response instanceof ServletServerHttpResponse servletResponse) {
            return servletResponse.getServletResponse().getStatus();
        }
        return 200;
    }
}
