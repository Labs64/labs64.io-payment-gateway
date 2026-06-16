package io.labs64.paymentgateway.correlation;

import java.util.Optional;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.MDC;

import static io.labs64.paymentgateway.correlation.CorrelationConstants.MDC_CORRELATION_ID;

/**
 * Provides access to the current correlation id stored in logging context.
 */
public final class CorrelationContextHolder {

    private CorrelationContextHolder() {
    }

    public static Optional<String> get() {
        return Optional.ofNullable(MDC.get(MDC_CORRELATION_ID))
                .filter(correlationId -> !correlationId.isBlank());
    }

    public static String require() {
        return get()
                .orElseThrow(() -> new IllegalStateException("Correlation id is not available in the current context."));
    }

    public static void set(final String correlationId) {
        if (StringUtils.isBlank(correlationId)) {
            clear();
            return;
        }
        MDC.put(MDC_CORRELATION_ID, correlationId);
    }

    public static void clear() {
        MDC.remove(MDC_CORRELATION_ID);
    }
}
