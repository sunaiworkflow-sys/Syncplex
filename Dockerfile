# ==============================================
# SyncPlex JD-Resume Matching Engine - Dockerfile
# Multi-stage build for optimized production image
# ==============================================

# ---- Build Stage ----
FROM eclipse-temurin:17-jdk-alpine AS builder

WORKDIR /app

# Install required tools
RUN apk add --no-cache curl tar

# Copy Maven wrapper and configuration
COPY springboot-backend/mvnw ./
COPY springboot-backend/.mvn ./.mvn
COPY springboot-backend/pom.xml ./

# Make mvnw executable
RUN chmod +x mvnw

# Download dependencies (cached layer - only re-runs when pom.xml changes)
RUN ./mvnw dependency:go-offline -B

# Copy source code
COPY springboot-backend/src ./src

# Build the application (skip tests for faster builds)
RUN ./mvnw clean package -DskipTests -B

# ---- Production Stage ----
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

# Add non-root user for security
RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring

# Copy the built JAR from builder stage
COPY --from=builder /app/target/jd-resume-matching-1.0.0.jar app.jar

# Expose port (will be overridden by $PORT in cloud environments)
EXPOSE 8080

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD wget --no-verbose --tries=1 --spider http://localhost:8080/api/health || exit 1

# JVM optimization for containers
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -Djava.security.egd=file:/dev/./urandom"

# Run the application
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar --server.port=${PORT:-8080}"]
