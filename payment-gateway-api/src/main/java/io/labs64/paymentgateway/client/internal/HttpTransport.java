package io.labs64.paymentgateway.client.internal;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.labs64.paymentgateway.client.AccessTokenProvider;
import io.labs64.paymentgateway.client.CallOptions;
import io.labs64.paymentgateway.client.CorrelationProvider;
import io.labs64.paymentgateway.client.PaymentGatewayApiException;
import io.labs64.paymentgateway.client.PaymentGatewayAuthenticationException;
import io.labs64.paymentgateway.client.PaymentGatewaySerializationException;
import io.labs64.paymentgateway.client.PaymentGatewayTimeoutException;
import io.labs64.paymentgateway.client.PaymentGatewayTransportException;
import io.labs64.paymentgateway.model.ErrorResponse;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

class HttpTransport {

    private static final String CORRELATION_HEADER = "X-Correlation-ID";
    private static final String IDEMPOTENCY_HEADER = "Idempotency-Key";

    private final URI baseUrl;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final AccessTokenProvider accessTokenProvider;
    private final CorrelationProvider correlationProvider;
    private final Duration callTimeout;

    HttpTransport(
            final URI baseUrl,
            final HttpClient httpClient,
            final AccessTokenProvider accessTokenProvider,
            final CorrelationProvider correlationProvider,
            final Duration callTimeout) {
        this.baseUrl = Objects.requireNonNull(baseUrl, "baseUrl");
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient");
        this.accessTokenProvider = accessTokenProvider;
        this.correlationProvider = correlationProvider;
        this.callTimeout = callTimeout;
        this.objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    }

    <T> T get(final String path, final QueryParameters query, final Class<T> responseType,
            final CallOptions options) {
        return send("GET", path, query, null, responseType, options);
    }

    <T> T post(final String path, final Object body, final Class<T> responseType, final CallOptions options) {
        return send("POST", path, new QueryParameters(), body, responseType, options);
    }

    <T> T patch(final String path, final Object body, final Class<T> responseType, final CallOptions options) {
        return send("PATCH", path, new QueryParameters(), body, responseType, options);
    }

    void delete(final String path, final CallOptions options) {
        send("DELETE", path, new QueryParameters(), null, Void.class, options);
    }

    private <T> T send(
            final String method,
            final String path,
            final QueryParameters query,
            final Object body,
            final Class<T> responseType,
            final CallOptions suppliedOptions) {
        final CallOptions options = suppliedOptions == null ? CallOptions.empty() : suppliedOptions;
        final URI uri = URI.create(baseUrl + path + query.suffix());
        final HttpRequest.Builder request = HttpRequest.newBuilder(uri)
                .header("Accept", "application/json")
                .method(method, bodyPublisher(body));

        if (body != null) {
            request.header("Content-Type", "application/json");
        }

        final Duration effectiveTimeout = options.timeout() != null ? options.timeout() : callTimeout;
        if (effectiveTimeout != null) {
            request.timeout(effectiveTimeout);
        }

        options.headers().forEach(request::setHeader);
        applyAuthorization(request);
        resolveCorrelationId(options).ifPresent(value -> request.setHeader(CORRELATION_HEADER, value));

        if (options.idempotencyKey() != null) {
            request.setHeader(IDEMPOTENCY_HEADER, options.idempotencyKey());
        }

        try {
            final HttpResponse<String> response = httpClient.send(
                    request.build(), HttpResponse.BodyHandlers.ofString());
            validateResponse(response);

            if (responseType == Void.class) {
                return null;
            }

            if (response.body() == null || response.body().isBlank()) {
                throw new PaymentGatewaySerializationException(
                        "Payment Gateway returned an empty response body for " + method + " " + path, null);
            }

            return objectMapper.readValue(response.body(), responseType);
        } catch (HttpTimeoutException exception) {
            throw new PaymentGatewayTimeoutException("Payment Gateway call timed out: " + method + " " + path,
                    exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new PaymentGatewayTransportException("Payment Gateway call was interrupted", exception);
        } catch (JsonProcessingException exception) {
            throw new PaymentGatewaySerializationException("Cannot decode Payment Gateway response", exception);
        } catch (IOException exception) {
            throw new PaymentGatewayTransportException("Cannot call Payment Gateway: " + method + " " + path,
                    exception);
        }
    }

    private HttpRequest.BodyPublisher bodyPublisher(final Object body) {
        if (body == null) {
            return HttpRequest.BodyPublishers.noBody();
        }
        try {
            return HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body));
        } catch (JsonProcessingException exception) {
            throw new PaymentGatewaySerializationException("Cannot encode Payment Gateway request", exception);
        }
    }

    private void applyAuthorization(final HttpRequest.Builder request) {
        if (accessTokenProvider == null) {
            return;
        }
        final String token;
        try {
            token = accessTokenProvider.accessToken();
        } catch (RuntimeException exception) {
            throw new PaymentGatewayAuthenticationException("Cannot obtain Payment Gateway access token", exception);
        }
        if (token == null || token.isBlank()) {
            throw new PaymentGatewayAuthenticationException("Access token provider returned a blank token", null);
        }
        request.setHeader("Authorization", "Bearer " + token.trim());
    }

    private Optional<String> resolveCorrelationId(final CallOptions options) {
        if (options.correlationId() != null) {
            return Optional.of(options.correlationId());
        }
        if (correlationProvider == null) {
            return Optional.empty();
        }
        try {
            return correlationProvider.correlationId()
                    .map(String::trim)
                    .filter(value -> !value.isEmpty());
        } catch (RuntimeException exception) {
            throw new PaymentGatewayTransportException("Cannot obtain correlation id", exception);
        }
    }

    protected void validateResponse(final HttpResponse<String> response) {
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw apiException(response);
        }
    }

    protected PaymentGatewayApiException apiException(final HttpResponse<String> response) {
        ErrorResponse error = null;
        if (response.body() != null && !response.body().isBlank()) {
            try {
                error = objectMapper.readValue(response.body(), ErrorResponse.class);
            } catch (JsonProcessingException ignored) {
                // The HTTP status and headers remain available even for a non-standard error body.
            }
        }
        final String correlationId = response.headers().firstValue(CORRELATION_HEADER).orElse(null);
        if (response.statusCode() == 401 || response.statusCode() == 403) {
            return new PaymentGatewayAuthenticationException(
                    response.statusCode(), error, correlationId, response.headers());
        }
        return new PaymentGatewayApiException(response.statusCode(), error, correlationId, response.headers());
    }
}
