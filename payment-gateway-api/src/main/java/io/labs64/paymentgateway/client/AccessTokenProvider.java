package io.labs64.paymentgateway.client;

/** Supplies a fresh access token for an API call. */
@FunctionalInterface
public interface AccessTokenProvider {

    String accessToken();
}
