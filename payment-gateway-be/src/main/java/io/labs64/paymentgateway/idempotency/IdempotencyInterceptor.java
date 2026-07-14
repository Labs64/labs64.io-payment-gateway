package io.labs64.paymentgateway.idempotency;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.labs64.paymentgateway.exception.TenantRequiredException;
import io.labs64.paymentgateway.service.IdempotencyService;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;

import io.labs64.authcontext.core.AuthContextHolder;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class IdempotencyInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(IdempotencyInterceptor.class);

    public static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";
    public static final String REQUEST_CONTEXT_ATTRIBUTE = IdempotencyInterceptor.class.getName() + ".context";

    private final IdempotencyService idempotencyService;
    private final ObjectMapper objectMapper;

    @Override
    public boolean preHandle(
            final HttpServletRequest request,
            final HttpServletResponse response,
            final Object handler) throws IOException {
        if (!(handler instanceof HandlerMethod handlerMethod)
                || !handlerMethod.hasMethodAnnotation(IdempotentOperation.class)) {
            return true;
        }

        final String idempotencyKey = request.getHeader(IDEMPOTENCY_KEY_HEADER);
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return true;
        }

        final IdempotencyContext context = new IdempotencyContext(
                requireTenantId(),
                idempotencyKey,
                requestHash(request),
                new IdempotencyOperation(request.getMethod(), pathPattern(request)));

        final Optional<IdempotencyResponse> cachedResponse = idempotencyService.startOrReplay(context);
        if (cachedResponse.isEmpty()) {
            request.setAttribute(REQUEST_CONTEXT_ATTRIBUTE, context);
            return true;
        }

        log.info("Idempotent replay | key={}, method={}, path={}, status={}",
                idempotencyKey, request.getMethod(), request.getRequestURI(), cachedResponse.get().status());
        writeCachedResponse(response, cachedResponse.get());
        return false;
    }

    private String pathPattern(final HttpServletRequest request) {
        final Object pattern = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        if (pattern instanceof String pathPattern && !pathPattern.isBlank()) {
            return pathPattern;
        }
        return request.getRequestURI();
    }

    private void writeCachedResponse(
            final HttpServletResponse response,
            final IdempotencyResponse cachedResponse) throws IOException {
        response.setStatus(cachedResponse.status());
        cachedResponse.headers().forEach((name, values) -> values.forEach(value -> response.addHeader(name, value)));

        if (cachedResponse.body() != null) {
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            objectMapper.writeValue(response.getOutputStream(), cachedResponse.body());
        }
    }

    private String requestHash(final HttpServletRequest request) {
        final MessageDigest digest = sha256();
        update(digest, requireTenantId());
        update(digest, request.getMethod());
        update(digest, request.getRequestURI());
        update(digest, request.getQueryString());
        digest.update(requestBody(request));
        return HexFormat.of().formatHex(digest.digest());
    }

    private MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 digest is not available.", ex);
        }
    }

    private void update(final MessageDigest digest, final String value) {
        if (value != null) {
            digest.update(value.getBytes(StandardCharsets.UTF_8));
        }
        digest.update((byte) 0);
    }

    private byte[] requestBody(final HttpServletRequest request) {
        if (request instanceof CachedBodyHttpServletRequest cachedRequest) {
            return cachedRequest.getCachedBody();
        }
        return new byte[0];
    }

    private String requireTenantId() {
        final String tenantId = AuthContextHolder.require().tenantId();

        if (StringUtils.isBlank(tenantId)) {
            throw new TenantRequiredException();
        }

        return tenantId;
    }
}
