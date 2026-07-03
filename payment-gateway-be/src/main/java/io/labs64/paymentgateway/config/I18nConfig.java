package io.labs64.paymentgateway.config;

import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.MessageSourceAccessor;

@Configuration
public class I18nConfig {
    @Bean
    public MessageSourceAccessor messageSourceAccessor(final MessageSource messageSource) {
        return new MessageSourceAccessor(messageSource);
    }
}