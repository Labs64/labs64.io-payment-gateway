package io.labs64.paymentgateway.client;

import io.labs64.paymentgateway.model.ErrorResponse;
import java.net.http.HttpHeaders;

/** A non-successful HTTP response returned by the Payment Gateway API. */
public class PaymentGatewayApiException extends PaymentGatewayException {

    private final int statusCode;
    private final ErrorResponse error;
    private final String correlationId;
    private final HttpHeaders headers;

    public PaymentGatewayApiException(
            final int statusCode,
            final ErrorResponse error,
            final String correlationId,
            final HttpHeaders headers) {
        this(message(statusCode, error), null, statusCode, error, correlationId, headers);
    }

    protected PaymentGatewayApiException(
            final String message,
            final Throwable cause,
            final int statusCode,
            final ErrorResponse error,
            final String correlationId,
            final HttpHeaders headers) {
        super(message, cause);
        this.statusCode = statusCode;
        this.error = error;
        this.correlationId = correlationId;
        this.headers = headers;
    }

    public int statusCode() {
        return statusCode;
    }

    public ErrorResponse error() {
        return error;
    }

    public String correlationId() {
        return correlationId;
    }

    public HttpHeaders headers() {
        return headers;
    }

    private static String message(final int statusCode, final ErrorResponse error) {
        if (error != null && error.getMessage() != null) {
            return "Payment Gateway returned HTTP " + statusCode + ": " + error.getMessage();
        }
        return "Payment Gateway returned HTTP " + statusCode;
    }
}
