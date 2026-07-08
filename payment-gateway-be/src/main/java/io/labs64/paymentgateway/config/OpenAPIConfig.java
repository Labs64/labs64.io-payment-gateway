package io.labs64.paymentgateway.config;

import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.servers.Server;

/**
 * Runtime OpenAPI servers and security for springdoc (the YAML spec's servers
 * and securitySchemes blocks only affect code generation). The gateway URL makes
 * Swagger UI "Try it out" work through Traefik, which owns and strips the
 * /payment-gateway/api/v1 prefix; the bearer security scheme renders the
 * "Authorize" button so a JWT can be attached to requests.
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
        },
        security = {
                @SecurityRequirement(name = "bearerAuth")
        }
)
@SecurityScheme(
        name = "bearerAuth",
        type = SecuritySchemeType.HTTP,
        scheme = "bearer",
        bearerFormat = "JWT",
        description = "JWT authentication token"
)
public class OpenAPIConfig {
}
