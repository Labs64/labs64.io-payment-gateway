package io.labs64.paymentgateway.client;

import io.labs64.paymentgateway.model.ErrorResponse;
import java.net.http.HttpHeaders;

/** Authentication or authorization failure. */
public final class PaymentGatewayAuthenticationException extends PaymentGatewayApiException {

    public PaymentGatewayAuthenticationException(
            final int statusCode,
            final ErrorResponse error,
            final String correlationId,
            final HttpHeaders headers) {
        super(statusCode, error, correlationId, headers);
    }

    public PaymentGatewayAuthenticationException(final String message, final Throwable cause) {
        super(message, cause, 0, null, null,
                HttpHeaders.of(java.util.Map.of(), (name, value) -> true));
    }
}
