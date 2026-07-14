package io.labs64.paymentgateway.idempotency;

import java.io.IOException;

import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class IdempotencyRequestBodyCachingFilter extends OncePerRequestFilter {

    @Override
    protected boolean shouldNotFilter(final HttpServletRequest request) {
        final String idempotencyKey = request.getHeader(IdempotencyInterceptor.IDEMPOTENCY_KEY_HEADER);
        return idempotencyKey == null || idempotencyKey.isBlank();
    }

    @Override
    protected void doFilterInternal(
            final HttpServletRequest request,
            final HttpServletResponse response,
            final FilterChain filterChain) throws ServletException, IOException {
        final byte[] body = StreamUtils.copyToByteArray(request.getInputStream());
        filterChain.doFilter(new CachedBodyHttpServletRequest(request, body), response);
    }
}
