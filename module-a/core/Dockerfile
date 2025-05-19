FROM eclipse-temurin:21-alpine

RUN addgroup -g 1064 appgroup && \
    adduser -D -u 1064 -G appgroup appuser

WORKDIR /home/appuser

COPY --chown=appuser:appgroup target/*.jar /home/appuser/app.jar

USER appuser

ENTRYPOINT ["java", "-jar", "/home/appuser/app.jar"]