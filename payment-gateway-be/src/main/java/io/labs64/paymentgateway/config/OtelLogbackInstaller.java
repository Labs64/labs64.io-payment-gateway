package io.labs64.paymentgateway.config;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.logback.appender.v1_0.OpenTelemetryAppender;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

/**
 * Connects the Spring-managed OpenTelemetry instance to the Logback OpenTelemetryAppender.
 *
 * Spring Boot 4.1's spring-boot-opentelemetry auto-configuration creates the SDK and exports
 * logs via OTLP, but never calls OpenTelemetryAppender.install(). Without this call the
 * Logback appender declared in logback-spring.xml silently drops every log event. This
 * listener runs once after the application context is fully started and performs the wiring.
 */
@Component
public class OtelLogbackInstaller implements ApplicationListener<ApplicationReadyEvent> {

    private final OpenTelemetry openTelemetry;

    public OtelLogbackInstaller(OpenTelemetry openTelemetry) {
        this.openTelemetry = openTelemetry;
    }

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        OpenTelemetryAppender.install(openTelemetry);
    }
}
