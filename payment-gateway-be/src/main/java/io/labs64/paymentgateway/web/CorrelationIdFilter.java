package io.labs64.paymentgateway.web;

import java.io.IOException;
import java.util.UUID;

import io.labs64.paymentgateway.correlation.CorrelationContextHolder;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import static io.labs64.paymentgateway.correlation.CorrelationConstants.CORRELATION_ID_HEADER;

/**
 * Servlet filter that ensures every request has an {@code X-Correlation-ID} header.
 * If absent, generates a new UUID. Places it in MDC for structured logging.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {
    @Override
    protected void doFilterInternal(@NotNull HttpServletRequest request,
                                    @NotNull HttpServletResponse response,
                                    @NotNull FilterChain filterChain) throws ServletException, IOException {
        String correlationId = request.getHeader(CORRELATION_ID_HEADER);

        if (StringUtils.isBlank(correlationId)) {
            correlationId = UUID.randomUUID().toString();
        }

        CorrelationContextHolder.set(correlationId);
        response.setHeader(CORRELATION_ID_HEADER, correlationId);

        try {
            filterChain.doFilter(request, response);
        } finally {
            CorrelationContextHolder.clear();
        }
    }
}
