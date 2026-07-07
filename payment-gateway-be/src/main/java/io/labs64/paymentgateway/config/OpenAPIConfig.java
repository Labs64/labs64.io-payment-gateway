package io.labs64.paymentgateway.config;

import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.servers.Server;

/**
 * Runtime OpenAPI servers for springdoc (the YAML spec's servers block only
 * affects code generation). The gateway URL makes Swagger UI "Try it out"
 * work through Traefik, which owns and strips the /payment-gateway/api/v1 prefix.
 */
@Configuration
@OpenAPIDefinition(
        servers = {
                @Server(
                        url = "/payment-gateway/api/v1",
                        description = "Via API Gateway (Traefik owns and strips the version prefix)"
                ),
                @Server(
                        url = "/",
                        description = "Local Development Server (direct, root-mapped)"
                )
        }
)
public class OpenAPIConfig {
}
