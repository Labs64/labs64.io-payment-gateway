package io.labs64.paymentgateway.repository;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

import io.labs64.paymentgateway.config.PaymentGatewayProperties;
import io.labs64.paymentgateway.idempotency.IdempotencyContext;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class RedisIdempotencyRepository {

    private static final String KEY_PREFIX = "payment-gateway:idempotency:";
    private static final DefaultRedisScript<List> START_OR_REPLAY_SCRIPT = script(
            "redis/idempotency/start-or-replay.lua", List.class);
    private static final DefaultRedisScript<String> COMPLETE_SCRIPT = script(
            "redis/idempotency/complete.lua", String.class);

    private final StringRedisTemplate redisTemplate;
    private final PaymentGatewayProperties properties;

    public StartResult startOrReplay(final IdempotencyContext context) {
        final List<?> result = redisTemplate.execute(
                START_OR_REPLAY_SCRIPT,
                List.of(redisKey(context)),
                context.requestHash(),
                context.executionToken(),
                Long.toString(properties.getIdempotency().getRedisTtl().toMillis()),
                Long.toString(properties.getIdempotency().getProcessingTimeout().toMillis()));

        if (result == null || result.isEmpty()) {
            throw new IllegalStateException("Redis returned no idempotency result.");
        }

        final StartStatus status = parseStartStatus(result.getFirst());
        if (status != StartStatus.COMPLETED) {
            return new StartResult(status, null, null, null);
        }
        if (result.size() != 4) {
            throw new IllegalStateException("Redis returned an incomplete idempotency response.");
        }
        return new StartResult(
                status,
                String.valueOf(result.get(1)),
                String.valueOf(result.get(2)),
                String.valueOf(result.get(3)));
    }

    public CompletionStatus complete(
            final IdempotencyContext context,
            final int responseStatus,
            final String responseHeaders,
            final String responseBody) {
        final String result = redisTemplate.execute(
                COMPLETE_SCRIPT,
                List.of(redisKey(context)),
                context.requestHash(),
                context.executionToken(),
                Integer.toString(responseStatus),
                responseHeaders,
                responseBody,
                Long.toString(properties.getIdempotency().getRedisTtl().toMillis()));

        try {
            return CompletionStatus.valueOf(result);
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new IllegalStateException("Unexpected Redis idempotency completion result: " + result, exception);
        }
    }

    private StartStatus parseStartStatus(final Object value) {
        try {
            return StartStatus.valueOf(String.valueOf(value));
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("Unexpected Redis idempotency result: " + value, exception);
        }
    }

    private String redisKey(final IdempotencyContext context) {
        final MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 digest is not available.", exception);
        }
        update(digest, context.tenantId());
        update(digest, context.operation().key());
        update(digest, context.idempotencyKey());
        return KEY_PREFIX + HexFormat.of().formatHex(digest.digest());
    }

    private void update(final MessageDigest digest, final String value) {
        digest.update(value.getBytes(StandardCharsets.UTF_8));
        digest.update((byte) 0);
    }

    private static <T> DefaultRedisScript<T> script(final String path, final Class<T> resultType) {
        final DefaultRedisScript<T> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource(path));
        script.setResultType(resultType);
        return script;
    }

    public enum StartStatus {
        STARTED,
        COMPLETED,
        PROCESSING,
        CONFLICT
    }

    public enum CompletionStatus {
        COMPLETED,
        MISSING,
        CONFLICT,
        STALE_OWNER
    }

    public record StartResult(
            StartStatus status,
            String responseStatus,
            String responseHeaders,
            String responseBody) {
    }
}
