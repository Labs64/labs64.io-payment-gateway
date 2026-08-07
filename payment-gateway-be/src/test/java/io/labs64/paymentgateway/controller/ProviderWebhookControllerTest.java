package io.labs64.paymentgateway.controller;

import io.labs64.paymentgateway.psp.spi.WebhookRequest;
import io.labs64.paymentgateway.service.WebhookService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ProviderWebhookControllerTest {
    @Mock
    private WebhookService webhookService;

    @Test
    void forwardsBodyAndTransportMetadataWithoutParsingProviderPayload() {
        final String body = "{ \"id\" : \"evt_123\" }";
        final MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Stripe-Signature", "t=123,v1=signature");
        request.addParameter("source", "stripe");
        final ProviderWebhookController controller = new ProviderWebhookController(webhookService, request);

        controller.handleProviderWebhook("stripe", body);

        final ArgumentCaptor<WebhookRequest> captor = ArgumentCaptor.forClass(WebhookRequest.class);
        verify(webhookService).processWebhook(captor.capture());
        assertThat(captor.getValue().provider()).isEqualTo("stripe");
        assertThat(captor.getValue().body()).isEqualTo(body);
        assertThat(captor.getValue().headers()).containsKey("Stripe-Signature");
        assertThat(captor.getValue().queryParams()).containsEntry("source", java.util.List.of("stripe"));
    }

    @Test
    void forwardsPayloadWithoutValidatingItsFormat() {
        final ProviderWebhookController controller = new ProviderWebhookController(
                webhookService, new MockHttpServletRequest());

        controller.handleProviderWebhook("custom", "provider-specific-body");

        final ArgumentCaptor<WebhookRequest> captor = ArgumentCaptor.forClass(WebhookRequest.class);
        verify(webhookService).processWebhook(captor.capture());
        assertThat(captor.getValue().body()).isEqualTo("provider-specific-body");
    }
}
