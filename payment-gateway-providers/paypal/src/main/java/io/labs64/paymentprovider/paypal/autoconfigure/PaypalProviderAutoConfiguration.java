package io.labs64.paymentprovider.paypal.autoconfigure;

import io.labs64.paymentgateway.psp.providers.paypal.PaypalClientFactory;
import io.labs64.paymentgateway.psp.providers.paypal.PaypalClientProperties;
import io.labs64.paymentgateway.psp.providers.paypal.PaypalPaymentProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/** Self-contained Spring registration for the PayPal provider module. */
@AutoConfiguration
@EnableConfigurationProperties(PaypalClientProperties.class)
public class PaypalProviderAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(PaypalClientFactory.class)
    PaypalClientFactory paypalClientFactory(final PaypalClientProperties properties) {
        return new PaypalClientFactory(properties);
    }

    @Bean
    @ConditionalOnMissingBean(PaypalPaymentProvider.class)
    PaypalPaymentProvider paypalPaymentProvider(final PaypalClientFactory clientFactory) {
        return new PaypalPaymentProvider(clientFactory);
    }
}
