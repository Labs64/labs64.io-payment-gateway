package io.labs64.paymentgateway.controller;

import io.labs64.paymentgateway.api.HealthApi;
import io.labs64.paymentgateway.correlation.CorrelationContextHolder;
import io.labs64.paymentgateway.model.Ping200Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController implements HealthApi {

    private static final Logger log = LoggerFactory.getLogger(HealthController.class);

    @Override
    public ResponseEntity<Ping200Response> ping() {
        log.debug("GET /ping | correlationId={}", MDC.get(CorrelationContextHolder.get().orElse("-")));

        Ping200Response response = new Ping200Response();
        response.setMessage("pong");

        return ResponseEntity.ok(response);
    }
}
