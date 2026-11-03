# ClearWay API image. No secrets here: they come from .env at run time (see docker-compose.yml).

# --- Build ---
FROM eclipse-temurin:17-jdk-alpine AS build
WORKDIR /src

# Download dependencies first so code changes don't redo this layer.
COPY gradlew settings.gradle build.gradle ./
COPY gradle gradle
RUN chmod +x gradlew && ./gradlew --no-daemon dependencies > /dev/null

COPY src src
RUN ./gradlew --no-daemon bootJar -x test \
    && cp build/libs/*-SNAPSHOT.jar app.jar

# --- Run ---
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
RUN addgroup -S app && adduser -S app -G app
COPY --from=build /src/app.jar app.jar
USER app

EXPOSE 8090
HEALTHCHECK --interval=15s --timeout=5s --start-period=60s --retries=5 \
    CMD wget -qO- http://localhost:8090/actuator/health | grep -q UP || exit 1

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
