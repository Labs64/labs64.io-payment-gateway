package io.labs64.paymentgateway.message;

import lombok.RequiredArgsConstructor;
import org.springframework.context.support.MessageSourceAccessor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class Messages {
    private final MessageSourceAccessor m;

    public String get(final String code, final Object... args) {
        return m.getMessage(code, args);
    }
}