package io.labs64.paymentgateway.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class CorsConfig implements WebMvcConfigurer {

    private static final Logger log = LoggerFactory.getLogger(CorsConfig.class);

    @Override
    public void addCorsMappings(final CorsRegistry registry) {
        log.info("CORS configured — origins: *, methods: GET/POST/PUT/PATCH/DELETE/OPTIONS, credentials: false");
        registry.addMapping("/**").allowedOriginPatterns("*")
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS").allowedHeaders("*")
                .exposedHeaders("Location", "X-Correlation-ID", "X-Request-Id").allowCredentials(false).maxAge(3600);
    }
}
