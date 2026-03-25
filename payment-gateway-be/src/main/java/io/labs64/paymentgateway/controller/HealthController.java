package io.labs64.paymentgateway.controller;

import io.labs64.paymentgateway.v1.api.HealthApi;
import io.labs64.paymentgateway.v1.model.Ping200Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class HealthController implements HealthApi {

    private static final Logger log = LoggerFactory.getLogger(HealthController.class);

    @Override
    public ResponseEntity<Ping200Response> ping() {
        log.info("GET /ping - Health check requested");

        Ping200Response response = new Ping200Response();
        response.setMessage("pong");

        log.info("GET /ping - Returning pong response");
        return ResponseEntity.ok(response);
    }
}
