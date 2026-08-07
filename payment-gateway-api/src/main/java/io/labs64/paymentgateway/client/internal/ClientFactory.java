package io.labs64.paymentgateway.client.internal;

import io.labs64.paymentgateway.client.AccessTokenProvider;
import io.labs64.paymentgateway.client.CheckoutSessionOperations;
import io.labs64.paymentgateway.client.CorrelationProvider;
import io.labs64.paymentgateway.client.PaymentDefinitionOperations;
import io.labs64.paymentgateway.client.PaymentOperations;
import io.labs64.paymentgateway.client.PaymentProviderOperations;
import io.labs64.paymentgateway.client.PaymentTransactionOperations;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;

/** Internal bridge used by the public facade without exposing implementation types. */
public final class ClientFactory {

    private ClientFactory() {
    }

    public static Operations create(
            final URI baseUrl,
            final HttpClient httpClient,
            final AccessTokenProvider accessTokenProvider,
            final CorrelationProvider correlationProvider,
            final Duration callTimeout) {
        final HttpTransport transport = new HttpTransport(
                baseUrl, httpClient, accessTokenProvider, correlationProvider, callTimeout);
        return new Operations(
                new DefaultPaymentProviderOperations(transport),
                new DefaultPaymentOperations(transport),
                new DefaultPaymentTransactionOperations(transport),
                new DefaultPaymentDefinitionOperations(transport),
                new DefaultCheckoutSessionOperations(transport));
    }

    public record Operations(
            PaymentProviderOperations paymentProviders,
            PaymentOperations payments,
            PaymentTransactionOperations paymentTransactions,
            PaymentDefinitionOperations paymentDefinitions,
            CheckoutSessionOperations checkoutSessions) {
    }
}
