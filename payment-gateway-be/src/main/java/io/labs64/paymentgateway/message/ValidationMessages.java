package io.labs64.paymentgateway.message;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ValidationMessages {
    private final Messages msg;

    public String failed() {
        return msg.get("validation.failed");
    }

    public String invalidField(final String field, final String reason) {
        return msg.get("validation.invalid_field", field, reason);
    }

    public String endpointNotFound(final String path) {
        return msg.get("validation.endpoint_not_found", path);
    }
}
