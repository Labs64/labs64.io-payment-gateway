package io.labs64.paymentgateway.controller;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import io.labs64.paymentgateway.correlation.CorrelationContextHolder;
import io.labs64.paymentgateway.psp.spi.WebhookRequest;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import io.labs64.paymentgateway.service.WebhookService;
import io.labs64.paymentgateway.api.WebhooksApi;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class WebhookController implements WebhooksApi {

    private static final Logger log = LoggerFactory.getLogger(WebhookController.class);

    private final WebhookService webhookService;
    private final HttpServletRequest httpServletRequest;

    @Override
    public ResponseEntity<Void> handleWebhook(
            String provider,
            Map<String, Object> requestBody) {
        log.info("POST /webhooks/{} | correlationId={}, payloadKeys={}",
                provider, MDC.get(CorrelationContextHolder.get().orElse("-")),
                requestBody != null ? requestBody.keySet() : "null");

        webhookService.processWebhook(toWebhookRequest(provider, requestBody));
        return ResponseEntity.ok().build();
    }

    private WebhookRequest toWebhookRequest(final String provider, final Map<String, Object> requestBody) {
        return new WebhookRequest(
                provider,
                new byte[0],
                requestBody != null ? requestBody : Map.of(),
                headers(),
                queryParams());
    }

    private Map<String, List<String>> headers() {
        return Collections.list(httpServletRequest.getHeaderNames())
                .stream()
                .collect(Collectors.toUnmodifiableMap(
                        Function.identity(),
                        name -> Collections.list(httpServletRequest.getHeaders(name))));
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
