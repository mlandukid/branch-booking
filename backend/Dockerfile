# syntax=docker/dockerfile:1

# ---- build stage: compiles the application with the Maven wrapper ----
FROM eclipse-temurin:24-jdk-alpine AS build
WORKDIR /workspace

COPY mvnw pom.xml ./
COPY .mvn .mvn
RUN sed -i 's/\r$//' mvnw && chmod +x mvnw

COPY src src
# Tests run in CI. The image build only packages the application.
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -DskipTests package

# ---- runtime stage: small JRE image, non-root user ----
FROM eclipse-temurin:24-jre-alpine
RUN addgroup -S app && adduser -S app -G app
WORKDIR /app

COPY --from=build /workspace/target/booking-*.jar app.jar

USER app
EXPOSE 8080

HEALTHCHECK --interval=15s --timeout=3s --start-period=40s --retries=5 \
  CMD wget -qO- http://localhost:8080/actuator/health | grep -q '"status":"UP"' || exit 1

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]