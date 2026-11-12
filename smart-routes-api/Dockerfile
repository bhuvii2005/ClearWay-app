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
# 127.0.0.1, not localhost: localhost can resolve to IPv6 (::1), and Java listens on IPv4 only (below).
HEALTHCHECK --interval=15s --timeout=5s --start-period=60s --retries=5 \
    CMD wget -qO- http://127.0.0.1:8090/actuator/health | grep -q UP || exit 1

# IPv4 only: Docker's default network has no IPv6 route, but Google's hosts have IPv6 addresses,
# and the Gemini embedding client waited on them until its 20 s timeout (Coach answers lost
# their knowledge-base sources). Chat calls were not affected.
ENTRYPOINT ["java", "-Djava.net.preferIPv4Stack=true", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
