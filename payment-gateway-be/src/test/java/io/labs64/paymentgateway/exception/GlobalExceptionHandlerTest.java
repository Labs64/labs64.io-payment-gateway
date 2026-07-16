package io.labs64.paymentgateway.exception;

import io.labs64.paymentgateway.message.ValidationMessages;
import io.labs64.paymentgateway.model.ErrorCode;
import io.labs64.paymentgateway.model.ErrorResponse;
import io.labs64.paymentgateway.psp.spi.ProviderException;
import io.labs64.paymentgateway.psp.spi.ProviderExecutionException;
import io.labs64.paymentgateway.psp.spi.ProviderValidationException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler(mock(ValidationMessages.class));

    @Test
    void providerValidationExceptionReturnsBadRequest() {
        final ResponseEntity<ErrorResponse> response = handler.handleProviderValidationException(
                new ProviderValidationException("Invalid provider config."));

        assertError(response, HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_ERROR, "Invalid provider config.");
    }

    @Test
    void providerExecutionExceptionReturnsBadGateway() {
        final ResponseEntity<ErrorResponse> response = handler.handleProviderExecutionException(
                new ProviderExecutionException("Provider request failed."));

        assertError(response, HttpStatus.BAD_GATEWAY, ErrorCode.PSP_ERROR, "Provider request failed.");
    }

    @Test
    void genericProviderExceptionReturnsBadGateway() {
        final ResponseEntity<ErrorResponse> response = handler.handleProviderException(
                new ProviderException("Unexpected provider failure."));

        assertError(response, HttpStatus.BAD_GATEWAY, ErrorCode.PSP_ERROR, "Unexpected provider failure.");
    }

    private static void assertError(
            final ResponseEntity<ErrorResponse> response,
            final HttpStatus status,
            final ErrorCode code,
            final String message) {
        assertThat(response.getStatusCode()).isEqualTo(status);
        final ErrorResponse body = Objects.requireNonNull(response.getBody());
        assertThat(body.getCode()).isEqualTo(code);
        assertThat(body.getMessage()).isEqualTo(message);
    }
}
