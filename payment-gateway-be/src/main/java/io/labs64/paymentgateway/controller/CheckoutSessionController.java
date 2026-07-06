package io.labs64.paymentgateway.controller;

import java.util.UUID;

import io.labs64.paymentgateway.api.CheckoutSessionsApi;
import io.labs64.paymentgateway.entity.CheckoutSessionEntity;
import io.labs64.paymentgateway.mapper.CheckoutSessionConfirmationMapper;
import io.labs64.paymentgateway.model.CheckoutSessionConfirmation;
import io.labs64.paymentgateway.service.CheckoutSessionConfirmationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequiredArgsConstructor
public class CheckoutSessionController implements CheckoutSessionsApi {

    private final CheckoutSessionConfirmationService service;
    private final CheckoutSessionConfirmationMapper mapper;

    @Override
    public ResponseEntity<CheckoutSessionConfirmation> getCheckoutSessionConfirmation(final UUID sessionId) {
        log.info("Checkout session confirmation requested | sessionId={}", sessionId);

        final CheckoutSessionEntity entity = service.get(sessionId);
        final CheckoutSessionConfirmation response = mapper.toDto(entity);

        return ResponseEntity.ok(response);
    }
}
