# Use Eclipse Temurin Java 17 (lightweight, production-ready)
FROM eclipse-temurin:17-jdk-alpine AS builder

WORKDIR /app

# Copy Maven wrapper and pom
COPY springboot-backend/mvnw .
COPY springboot-backend/.mvn .mvn
COPY springboot-backend/pom.xml .

# Download dependencies (cached layer)
RUN chmod +x mvnw && ./mvnw dependency:go-offline -B

# Copy source code
COPY springboot-backend/src src

# Build the application
RUN ./mvnw package -DskipTests

# ---- Production Stage ----
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

# Copy the built JAR from builder stage
COPY --from=builder /app/target/jd-resume-matching-1.0.0.jar app.jar

# Expose port (Railway/Render will override with $PORT)
EXPOSE 8080

# Run the application
ENTRYPOINT ["java", "-jar", "app.jar"]
