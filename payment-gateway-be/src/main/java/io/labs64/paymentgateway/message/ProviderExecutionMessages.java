package io.labs64.paymentgateway.message;

import io.labs64.paymentgateway.psp.spi.ProviderExecutionFailure;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ProviderExecutionMessages {

    private final Messages msg;

    public String message(final ProviderExecutionFailure failure) {
        return switch (failure) {
            case UNAVAILABLE -> msg.get("provider_execution.unavailable");
            case AUTHENTICATION_FAILED -> msg.get("provider_execution.authentication_failed");
            case INVALID_RESPONSE -> msg.get("provider_execution.invalid_response");
        };
    }

    public String unexpected() {
        return msg.get("provider_execution.unexpected");
    }
}
