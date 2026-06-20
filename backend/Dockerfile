# Stage 1: Build the application
FROM maven:3.9.6-eclipse-temurin-21-alpine AS builder
WORKDIR /app

# Copy pom.xml to download dependencies first (leveraging Docker layer cache)
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copy the source code and compile
COPY src ./src
RUN mvn clean package -DskipTests -B

# Stage 2: Create the lightweight runtime container
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Create a system group and user to run the app securely as non-root
RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring

# Copy jar from builder stage
COPY --from=builder /app/target/*.jar app.jar

# Expose the default backend port
EXPOSE 8085

# Define entrypoint
ENTRYPOINT ["java", "-jar", "app.jar"]
