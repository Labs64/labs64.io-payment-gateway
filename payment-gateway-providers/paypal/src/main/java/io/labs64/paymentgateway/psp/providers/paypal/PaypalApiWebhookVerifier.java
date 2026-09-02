package io.labs64.paymentgateway.psp.providers.paypal;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;

import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.paypal.sdk.PaypalServerSdkClient;
import com.paypal.sdk.exceptions.ApiException;
import com.paypal.sdk.models.OAuthToken;
import io.labs64.paymentgateway.psp.spi.WebhookRejectedException;
import io.labs64.paymentgateway.psp.spi.WebhookRequest;
import org.apache.commons.lang3.StringUtils;

/**
 * PayPal REST API based webhook signature verifier.
 */
final class PaypalApiWebhookVerifier implements PaypalWebhookVerifier {

    private static final String VERIFY_PATH = "/v1/notifications/verify-webhook-signature";
    private static final String SUCCESS = "SUCCESS";
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);

    private static final String AUTH_ALGO = "PAYPAL-AUTH-ALGO";
    private static final String CERT_URL = "PAYPAL-CERT-URL";
    private static final String TRANSMISSION_ID = "PAYPAL-TRANSMISSION-ID";
    private static final String TRANSMISSION_SIG = "PAYPAL-TRANSMISSION-SIG";
    private static final String TRANSMISSION_TIME = "PAYPAL-TRANSMISSION-TIME";

    private final HttpClient httpClient;
    private final PaypalClientFactory clientFactory;

    PaypalApiWebhookVerifier(final PaypalClientFactory clientFactory) {
        this(HttpClient.newBuilder().connectTimeout(REQUEST_TIMEOUT).build(), clientFactory);
    }

    PaypalApiWebhookVerifier(final HttpClient httpClient, final PaypalClientFactory clientFactory) {
        this.httpClient = httpClient;
        this.clientFactory = clientFactory;
    }

    @Override
    public void verify(
            final PaypalServerSdkClient client,
            final String webhookId,
            final WebhookRequest request) {
        final JsonObject verificationRequest = new JsonObject();
        verificationRequest.addProperty("auth_algo", requiredHeader(request, AUTH_ALGO));
        verificationRequest.addProperty("cert_url", requiredHeader(request, CERT_URL));
        verificationRequest.addProperty("transmission_id", requiredHeader(request, TRANSMISSION_ID));
        verificationRequest.addProperty("transmission_sig", requiredHeader(request, TRANSMISSION_SIG));
        verificationRequest.addProperty("transmission_time", requiredHeader(request, TRANSMISSION_TIME));
        verificationRequest.addProperty("webhook_id", webhookId);
        try {
            verificationRequest.add("webhook_event", JsonParser.parseString(request.body()));
        } catch (JsonParseException | IllegalStateException ex) {
            throw new WebhookRejectedException("PayPal webhook payload is invalid.", ex);
        }

        final OAuthToken token = accessToken(client);
        final HttpRequest verificationHttpRequest = HttpRequest.newBuilder()
                .uri(URI.create(clientFactory.apiBaseUri(client) + VERIFY_PATH))
                .timeout(REQUEST_TIMEOUT)
                .header("Authorization", token.getTokenType() + " " + token.getAccessToken())
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(verificationRequest.toString()))
                .build();

        final HttpResponse<String> response;
        try {
            response = httpClient.send(verificationHttpRequest, HttpResponse.BodyHandlers.ofString());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new WebhookRejectedException("PayPal webhook verification was interrupted.", ex);
        } catch (IOException ex) {
            throw new WebhookRejectedException("PayPal webhook verification request failed.", ex);
        }

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new WebhookRejectedException(
                    "PayPal webhook verification failed with HTTP status " + response.statusCode() + ".");
        }

        try {
            final JsonObject body = JsonParser.parseString(response.body()).getAsJsonObject();
            if (!SUCCESS.equals(body.has("verification_status")
                    ? body.get("verification_status").getAsString()
                    : null)) {
                throw new WebhookRejectedException("PayPal webhook verification failed.");
            }
        } catch (JsonParseException | IllegalStateException | UnsupportedOperationException ex) {
            throw new WebhookRejectedException("PayPal webhook verification returned an invalid response.", ex);
        }
    }

    private static OAuthToken accessToken(final PaypalServerSdkClient client) {
        try {
            final OAuthToken current = client.getClientCredentialsAuth().getOAuthToken();
            final OAuthToken token = current != null && !client.getClientCredentialsAuth().isTokenExpired(current)
                    ? current
                    : client.getClientCredentialsAuth().fetchToken();
            if (token == null || StringUtils.isBlank(token.getAccessToken())
                    || StringUtils.isBlank(token.getTokenType())) {
                throw new WebhookRejectedException("PayPal webhook verification returned no access token.");
            }
            return token;
        } catch (ApiException | IOException ex) {
            throw new WebhookRejectedException("PayPal webhook verification authentication failed.", ex);
        }
    }

    private static String requiredHeader(final WebhookRequest request, final String name) {
        return firstHeader(request, name)
                .orElseThrow(() -> new WebhookRejectedException(
                        "PayPal webhook header is missing: " + name));
    }

    private static Optional<String> firstHeader(final WebhookRequest request, final String name) {
        if (request.headers() == null) {
            return Optional.empty();
        }
        return request.headers().entrySet().stream()
                .filter(entry -> entry.getKey().equalsIgnoreCase(name))
                .flatMap(entry -> entry.getValue().stream())
                .filter(StringUtils::isNotBlank)
                .findFirst();
    }
}
