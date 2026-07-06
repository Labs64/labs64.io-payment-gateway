package io.labs64.paymentgateway.controller;

import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import io.labs64.paymentgateway.api.ProviderCheckoutApi;
import io.labs64.paymentgateway.service.ProviderCheckoutService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class ProviderCheckoutController implements ProviderCheckoutApi {

    private final ProviderCheckoutService service;
    private final HttpServletRequest httpServletRequest;

    @Override
    public ResponseEntity<Void> returnProviderCheckoutSession(
            final String provider,
            final UUID sessionId) {
        return redirect(service.complete(provider, sessionId, queryParams()));
    }

    @Override
    public ResponseEntity<Void> cancelProviderCheckoutSession(
            final String provider,
            final UUID sessionId) {
        return redirect(service.cancel(provider, sessionId, queryParams()));
    }

    private ResponseEntity<Void> redirect(final URI location) {
        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, location.toString())
                .build();
    }

    private Map<String, List<String>> queryParams() {
        return httpServletRequest.getParameterMap()
                .entrySet()
                .stream()
                .collect(Collectors.toUnmodifiableMap(
                        Map.Entry::getKey,
                        entry -> List.copyOf(Arrays.asList(entry.getValue()))));
    }
}
