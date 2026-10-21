# Simple Dockerfile that works with the available Java environment
FROM ubuntu:20.04

# Set environment variables
ENV DEBIAN_FRONTEND=noninteractive
ENV JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
ENV PATH=$PATH:$JAVA_HOME/bin

# Install Java 17
RUN apt-get update && apt-get install -y \
    openjdk-17-jre-headless \
    && rm -rf /var/lib/apt/lists/*

# Create app directory
WORKDIR /app

# Copy the JAR file
COPY target/user-service-0.0.1-SNAPSHOT.jar app.jar

# Expose port
EXPOSE 8080

# Create a non-root user
RUN addgroup --system spring && adduser --system spring
USER spring:spring

# Health check using Spring Boot Actuator
HEALTHCHECK --interval=30s --timeout=10s --start-period=5s --retries=3 \
    CMD curl -f http://localhost:8080/actuator/health | grep -q '\"status\":\"UP\"' || exit 1

# Run the application
ENTRYPOINT ["java", "-jar", "app.jar"]