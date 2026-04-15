FROM eclipse-temurin:25-alpine

# Build arguments
ARG USER_ID=1064
ARG GROUP_ID=1064
ARG USER_NAME=l64user
ARG GROUP_NAME=l64group

# Create non-root group and user
RUN addgroup -g ${GROUP_ID} ${GROUP_NAME} && \
    adduser -D -u ${USER_ID} -G ${GROUP_NAME} ${USER_NAME}

WORKDIR /home/${USER_NAME}

# Copy jar with correct ownership
COPY --chown=${USER_NAME}:${GROUP_NAME} target/*.jar app.jar

EXPOSE 8080

# Liveness probe support
HEALTHCHECK --interval=15s --timeout=5s --start-period=30s --retries=3 \
    CMD wget -qO- http://localhost:8080/actuator/health/liveness || exit 1

USER ${USER_NAME}

ENTRYPOINT ["sh", "-c", "java ${JAVA_OPTS} -jar /home/${USER_NAME}/app.jar"]
