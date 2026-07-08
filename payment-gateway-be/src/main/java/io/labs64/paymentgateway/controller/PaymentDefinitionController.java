package io.labs64.paymentgateway.controller;

import io.labs64.paymentgateway.api.PaymentDefinitionsApi;
import io.labs64.paymentgateway.mapper.PaymentDefinitionMapper;
import io.labs64.paymentgateway.model.PaymentDefinitionListResponse;
import io.labs64.paymentgateway.service.PaymentDefinitionService;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequiredArgsConstructor
public class PaymentDefinitionController implements PaymentDefinitionsApi {
    private final PaymentDefinitionService service;
    private final PaymentDefinitionMapper mapper;

    /** Public path — overrides the global bearerAuth requirement so Swagger UI doesn't show it as locked. */
    @Override
    @SecurityRequirements
    public ResponseEntity<PaymentDefinitionListResponse> listPaymentDefinitions() {
        log.info("Payment definitions list requested");

        return ResponseEntity.ok(mapper.toListResponse(service.listEnabled()));
    }
}
