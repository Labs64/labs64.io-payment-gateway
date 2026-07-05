package io.labs64.paymentgateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;

import io.labs64.paymentgateway.security.DevAuthFilter;
import io.labs64.paymentgateway.security.Scopes;

@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(final HttpSecurity http,
            final ObjectProvider<DevAuthFilter> devAuthFilter) {
        devAuthFilter.ifAvailable(filter -> http.addFilterBefore(filter, AnonymousAuthenticationFilter.class));

        return http
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.GET, "/actuator/health/**").permitAll()
                        // Prometheus scrape endpoint — reached unauthenticated by the in-cluster
                        // scraper (pod annotation), restricted at the network layer. See OBSERVABILITY.md.
                        .requestMatchers(HttpMethod.GET, "/actuator/prometheus").permitAll()
                        // OpenAPI spec + Swagger UI — public docs, aggregated by the gateway at
                        // gateway.localhost/payment-gateway/v3/api-docs (see AGENTS.md).
                        .requestMatchers(HttpMethod.GET, "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html")
                        .permitAll()
                        .requestMatchers(HttpMethod.POST, "/webhooks/**").permitAll()
                        // list payment definitions
                        .requestMatchers(HttpMethod.GET, "/payment-definitions").permitAll()
                        // get single payment provider including config
                        .requestMatchers(HttpMethod.GET, "/payment-providers/*")
                        .hasAuthority(scope(Scopes.PAYMENT_PROVIDER_WRITE))
                        // list payment providers without config
                        .requestMatchers(HttpMethod.GET, "/payment-providers")
                        .hasAuthority(scope(Scopes.PAYMENT_PROVIDER_READ))
                        // create payment provider
                        .requestMatchers(HttpMethod.POST, "/payment-providers/**")
                        .hasAuthority(scope(Scopes.PAYMENT_PROVIDER_WRITE))
                        // update payment provider
                        .requestMatchers(HttpMethod.PATCH, "/payment-providers/**")
                        .hasAuthority(scope(Scopes.PAYMENT_PROVIDER_WRITE))
                        // delete payment provider
                        .requestMatchers(HttpMethod.DELETE, "/payment-providers/**")
                        .hasAuthority(scope(Scopes.PAYMENT_PROVIDER_WRITE))

                        .requestMatchers(HttpMethod.GET, "/payments/**")
                        .hasAuthority(scope(Scopes.PAYMENT_READ))
                        .requestMatchers(HttpMethod.POST, "/payments/*/pay")
                        .hasAuthority(scope(Scopes.PAYMENT_PAY))
                        .requestMatchers(HttpMethod.POST, "/payments/**")
                        .hasAuthority(scope(Scopes.PAYMENT_WRITE))
                        .requestMatchers(HttpMethod.GET, "/payment-transactions/**")
                        .hasAuthority(scope(Scopes.PAYMENT_TRANSACTION_READ))
                        .anyRequest().authenticated())
                .build();
    }

    private static String scope(final String scope) {
        return "SCOPE_" + scope;
    }
}
