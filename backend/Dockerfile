# ================================================================
# Stage 1: Build — Maven + JDK 21
# ================================================================
FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /build

# Copy dependency descriptors first for layer caching
COPY pom.xml .
# Download dependencies without building the full project
RUN mvn -B dependency:go-offline -q 2>/dev/null || true

# Copy source and build (skip tests — tests run in CI, not Docker build)
COPY src ./src
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B package -DskipTests -q

# ================================================================
# Stage 2: Runtime — JRE 21 slim
# ================================================================
FROM eclipse-temurin:21-jre-alpine AS runtime

# Non-root user for security
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser

WORKDIR /app

# Copy only the fat jar from the build stage
COPY --from=builder /build/target/*.jar app.jar

# Actuator health port exposed
EXPOSE 8080

# JVM tuning: set sensible defaults for a container
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -Djava.security.egd=file:/dev/./urandom"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
