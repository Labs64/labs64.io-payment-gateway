package io.labs64.paymentgateway.config;

import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.servers.Server;

import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.ClassPathResource;
import org.springdoc.core.customizers.OpenApiCustomizer;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.Contact;
import java.util.Properties;

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
                        description = "Via API Gateway (Traefik)"
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

    @Bean
    public OpenApiCustomizer openApiInfoCustomizer() {
        return openApi -> {
            try {
                YamlPropertiesFactoryBean yaml = new YamlPropertiesFactoryBean();
                yaml.setResources(new ClassPathResource("openapi/openapi-payment-gateway.yaml"));
                Properties props = yaml.getObject();
                if (props != null) {
                    Info info = new Info();
                    info.setTitle(props.getProperty("info.title"));
                    info.setVersion(props.getProperty("info.version"));
                    info.setDescription(props.getProperty("info.description"));
                    
                    Contact contact = new Contact();
                    contact.setName(props.getProperty("info.contact.name"));
                    contact.setUrl(props.getProperty("info.contact.url"));
                    contact.setEmail(props.getProperty("info.contact.email"));
                    info.setContact(contact);
                    
                    openApi.setInfo(info);
                }
            } catch (Exception e) {
                // fallback to whatever is generated
            }
        };
    }
}
