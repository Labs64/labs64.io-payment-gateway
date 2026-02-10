FROM eclipse-temurin:25-alpine

# Define build arguments
ARG USER_ID=1064
ARG GROUP_ID=1064
ARG USER_NAME=l64user
ARG GROUP_NAME=l64group

# Create group and user with the specified IDs
RUN addgroup -g ${GROUP_ID} ${GROUP_NAME} && \
    adduser -D -u ${USER_ID} -G ${GROUP_NAME} ${USER_NAME}

# Set working directory
WORKDIR /home/${USER_NAME}

# Copy the jar file with appropriate ownership
COPY --chown=${USER_NAME}:${GROUP_NAME} target/*.jar /home/${USER_NAME}/app.jar

# Set user context
USER ${USER_NAME}

# Default command
ENTRYPOINT ["/bin/sh", "-c", "java $JAVA_OPTS -jar /home/l64user/app.jar"]