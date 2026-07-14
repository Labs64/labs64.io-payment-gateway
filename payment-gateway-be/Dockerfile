FROM eclipse-temurin:25-jre

# Build arguments
ARG USER_ID=1064
ARG GROUP_ID=1064
ARG USER_NAME=l64user
ARG GROUP_NAME=l64group

# Create non-root group and user
RUN apt-get update && apt-get install -y wget && rm -rf /var/lib/apt/lists/* && \
    groupadd -g ${GROUP_ID} ${GROUP_NAME} && \
    useradd -m -u ${USER_ID} -g ${GROUP_NAME} -s /bin/bash ${USER_NAME}

# OpenTelemetry Java Agent — bundled but inert. Activated at deploy time via
# JAVA_TOOL_OPTIONS=-javaagent:/otel/opentelemetry-javaagent.jar (observability is an
# infrastructure concern; the same image runs with and without it). See OBSERVABILITY.md.
ARG OTEL_JAVAAGENT_VERSION=2.29.0
ADD https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/download/v${OTEL_JAVAAGENT_VERSION}/opentelemetry-javaagent.jar /otel/opentelemetry-javaagent.jar
RUN chmod 444 /otel/opentelemetry-javaagent.jar

WORKDIR /home/${USER_NAME}

# Copy jar with correct ownership
COPY --chown=${USER_NAME}:${GROUP_NAME} target/*.jar app.jar

EXPOSE 8080

# Liveness probe support
HEALTHCHECK --interval=15s --timeout=5s --start-period=30s --retries=3 \
    CMD wget -qO- http://localhost:8080/actuator/health/liveness || exit 1

USER ${USER_NAME}

ENTRYPOINT ["sh", "-c", "java ${JAVA_OPTS} -jar /home/l64user/app.jar"]
