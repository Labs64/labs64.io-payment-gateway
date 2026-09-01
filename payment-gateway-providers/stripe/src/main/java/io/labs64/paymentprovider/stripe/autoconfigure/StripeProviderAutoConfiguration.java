package io.labs64.paymentprovider.stripe.autoconfigure;

import io.labs64.paymentgateway.psp.providers.stripe.StripeClientFactory;
import io.labs64.paymentgateway.psp.providers.stripe.StripeClientProperties;
import io.labs64.paymentgateway.psp.providers.stripe.StripePaymentProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/** Self-contained Spring registration for the Stripe provider module. */
@AutoConfiguration
@EnableConfigurationProperties(StripeClientProperties.class)
public class StripeProviderAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(StripeClientFactory.class)
    StripeClientFactory stripeClientFactory(final StripeClientProperties properties) {
        return new StripeClientFactory(properties);
    }

    @Bean
    @ConditionalOnMissingBean(StripePaymentProvider.class)
    StripePaymentProvider stripePaymentProvider(final StripeClientFactory clientFactory) {
        return new StripePaymentProvider(clientFactory);
    }
}
