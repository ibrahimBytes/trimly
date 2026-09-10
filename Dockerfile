# ---------- Build stage ----------
FROM maven:3.9.6-eclipse-temurin-17 AS build

WORKDIR /app

# Copy dependency definition first for Docker layer caching
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copy application source
COPY src ./src
RUN mvn clean package -DskipTests

# ---------- Runtime stage ----------
FROM eclipse-temurin:17-jre-jammy

WORKDIR /app

# Run the application as a non-root user
RUN useradd --system --create-home --shell /usr/sbin/nologin trimly

# Copy the built application
COPY --from=build /app/target/URL-Shortener-0.0.1-SNAPSHOT.jar app.jar

RUN chown trimly:trimly app.jar

USER trimly

# Prefer IPv4 for environments where the container has no IPv6 route.
ENV JAVA_TOOL_OPTIONS="-Djava.net.preferIPv4Stack=true"

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
