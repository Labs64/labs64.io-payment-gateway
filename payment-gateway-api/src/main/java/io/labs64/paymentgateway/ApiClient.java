package io.labs64.paymentgateway;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Static utilities referenced by model sources generated with the OpenAPI
 * Generator native Java templates.
 *
 * <p>This is deliberately not an HTTP client. The architecture and public API
 * of a future Payment Gateway client will be designed separately.
 */
public final class ApiClient {

    private ApiClient() {
    }

    public static String urlEncode(final String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    public static String valueToString(final Object value) {
        if (value == null) {
            return "";
        }
        return value.toString();
    }
}
