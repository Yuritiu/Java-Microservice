# ---- Stage 1: build the jar ----
# Uses an image that already has Maven + JDK 17, so nothing needs installing on your PC.
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build

# Download dependencies first. This layer is cached until pom.xml changes,
# so rebuilds after a code change are much faster.
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

COPY src ./src
# Tests need a live Postgres, so they are skipped here and run separately (e.g. in CI).
RUN mvn -B -q package -DskipTests

# ---- Stage 2: the image that actually runs ----
# JRE only (no compiler, no Maven), so the final image is much smaller.
# eclipse-temurin publishes amd64 and arm64, which matters for Oracle's ARM servers.
FROM eclipse-temurin:17-jre
WORKDIR /app

RUN groupadd --system spring && useradd --system --gid spring spring
COPY --from=build /build/target/user-service-0.0.1-SNAPSHOT.jar app.jar
USER spring:spring

EXPOSE 8080

# Size the Java heap from the container's memory limit instead of the host's RAM.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0"

ENTRYPOINT ["java", "-jar", "app.jar"]
