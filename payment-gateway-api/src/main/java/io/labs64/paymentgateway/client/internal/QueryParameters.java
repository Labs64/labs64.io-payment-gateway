package io.labs64.paymentgateway.client.internal;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

final class QueryParameters {

    private final List<String> values = new ArrayList<>();

    QueryParameters add(final String name, final Object value) {
        if (value != null) {
            values.add(encode(name) + "=" + encode(value.toString()));
        }
        return this;
    }

    QueryParameters addAll(final String name, final Iterable<String> entries) {
        if (entries != null) {
            entries.forEach(value -> add(name, value));
        }
        return this;
    }

    String suffix() {
        return values.isEmpty() ? "" : "?" + String.join("&", values);
    }

    private static String encode(final String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
