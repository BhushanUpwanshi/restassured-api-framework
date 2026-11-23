# Runs the framework's hermetic suite (api-core against WireMock) on any machine with
# Docker and no JDK. No network calls to the API under test, so the result depends only
# on the code.
#
#   docker compose run --rm tests
#
# Dependencies are resolved in their own layer, so editing a test does not re-download
# the world on the next build.

FROM maven:3.9-eclipse-temurin-17

WORKDIR /workspace

COPY pom.xml .
COPY api-core/pom.xml api-core/
COPY api-tests/pom.xml api-tests/
COPY config config
RUN mvn -B -q -pl api-core dependency:go-offline

COPY api-core api-core

CMD ["mvn", "-B", "verify", "-pl", "api-core"]
