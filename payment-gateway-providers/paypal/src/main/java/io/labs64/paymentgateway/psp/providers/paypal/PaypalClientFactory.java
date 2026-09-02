package io.labs64.paymentgateway.psp.providers.paypal;

import java.net.URI;

import com.paypal.sdk.Environment;
import com.paypal.sdk.PaypalServerSdkClient;
import com.paypal.sdk.authentication.ClientCredentialsAuthModel;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Constructs tenant-authenticated PayPal SDK clients without leaking SDK setup into payment logic. */
public final class PaypalClientFactory {

    private static final Logger log = LoggerFactory.getLogger(PaypalClientFactory.class);

    private final URI apiBaseUrl;

    public PaypalClientFactory(final PaypalClientProperties properties) {
        this.apiBaseUrl = properties.apiBaseUrl();

        if (apiBaseUrl != null) {
            if (!apiBaseUrl.isAbsolute() || apiBaseUrl.getHost() == null) {
                throw new IllegalArgumentException("PayPal API base URL must be absolute.");
            }
            log.info("PayPal SDK API base URL overridden: {}", apiBaseUrl);
        }
    }

    public PaypalServerSdkClient create(
            final String clientId,
            final String clientSecret,
            final Environment environment) {
        final PaypalServerSdkClient.Builder builder = new PaypalServerSdkClient.Builder()
                .environment(environment)
                .clientCredentialsAuth(new ClientCredentialsAuthModel.Builder(clientId, clientSecret).build());

        if (apiBaseUrl != null) {
            final HttpUrl endpoint = HttpUrl.get(apiBaseUrl);
            final OkHttpClient httpClient = new OkHttpClient.Builder()
                    .addInterceptor(chain -> {
                        final HttpUrl requestUrl = chain.request().url();
                        final HttpUrl targetUrl = requestUrl.newBuilder()
                                .scheme(endpoint.scheme())
                                .host(endpoint.host())
                                .port(endpoint.port())
                                .build();
                        return chain.proceed(chain.request().newBuilder().url(targetUrl).build());
                    })
                    .build();
            builder.httpClientConfig(config -> config.httpClientInstance(httpClient));
        }

        return builder.build();
    }

    String apiBaseUri(final PaypalServerSdkClient client) {
        if (apiBaseUrl == null) {
            return client.getBaseUri();
        }
        final String value = apiBaseUrl.toString();
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
