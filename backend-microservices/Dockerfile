# Parameterised image for every Java module of the reactor.
#
#   docker build -f Dockerfile --build-arg MODULE=user-service -t sn/user-service .
#
# The build context is this directory (backend-microservices/). `common` is built automatically
# because every service module depends on it and `-am` pulls it into the reactor.

# ---------------------------------------------------------------- build stage
FROM maven:3.9-eclipse-temurin-21 AS build

ARG MODULE
WORKDIR /workspace

# Copy the POMs first so dependency resolution is cached independently of the sources.
COPY pom.xml ./
COPY common/pom.xml common/
COPY discovery-server/pom.xml discovery-server/
COPY api-gateway/pom.xml api-gateway/
COPY auth-service/pom.xml auth-service/
COPY user-service/pom.xml user-service/
COPY media-service/pom.xml media-service/
COPY notification-service/pom.xml notification-service/
COPY chat-service/pom.xml chat-service/
COPY moderation-service/pom.xml moderation-service/

# Best effort: warm the local repository. A failure here must not break the build.
RUN mvn -B -q -pl "${MODULE}" -am -DskipTests dependency:go-offline || true

COPY common common
COPY "${MODULE}" "${MODULE}"

RUN mvn -B -q -pl "${MODULE}" -am -DskipTests package \
 && cp "${MODULE}"/target/*.jar /workspace/app.jar

# ---------------------------------------------------------------- runtime stage
FROM eclipse-temurin:21-jre

# curl is only needed for the container health check.
RUN apt-get update \
 && apt-get install --no-install-recommends -y curl \
 && rm -rf /var/lib/apt/lists/* \
 && groupadd --system app \
 && useradd --system --gid app --home-dir /app app

WORKDIR /app
COPY --from=build --chown=app:app /workspace/app.jar app.jar

USER app
EXPOSE 8080

# Every service exposes management.endpoints.web.exposure.include=health on its own port.
HEALTHCHECK --interval=30s --timeout=5s --start-period=120s --retries=5 \
  CMD curl -fsS "http://localhost:${SERVER_PORT:-8080}/actuator/health" || exit 1

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
