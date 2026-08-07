package io.labs64.paymentgateway.controller;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import io.labs64.paymentgateway.api.ProviderWebhooksApi;
import io.labs64.paymentgateway.correlation.CorrelationContextHolder;
import io.labs64.paymentgateway.psp.spi.WebhookRequest;
import io.labs64.paymentgateway.service.WebhookService;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class ProviderWebhookController implements ProviderWebhooksApi {

    private static final Logger log = LoggerFactory.getLogger(ProviderWebhookController.class);

    private final WebhookService webhookService;
    private final HttpServletRequest httpServletRequest;

    /** Public path — overrides the global bearerAuth requirement so Swagger UI doesn't show it as locked. */
    @Override
    @SecurityRequirements
    public ResponseEntity<Void> handleProviderWebhook(
            final String provider,
            final String requestBody) {
        log.info("Provider webhook received | provider={}, correlationId={}",
                provider, CorrelationContextHolder.get().orElse("-"));

        webhookService.processWebhook(toWebhookRequest(provider, requestBody));
        return ResponseEntity.ok().build();
    }

    private WebhookRequest toWebhookRequest(final String provider, final String requestBody) {
        return new WebhookRequest(provider, requestBody, headers(), queryParams());
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
