# Multi-stage build. Tests are skipped here because they need Docker (Testcontainers);
# they run on the host with ./mvnw verify.

FROM maven:3-eclipse-temurin-26 AS build
WORKDIR /src
# POMs first: the dependency layer is cached until a POM changes.
COPY pom.xml .
COPY mini-wsa-app/pom.xml mini-wsa-app/
COPY mini-wsa-generator/pom.xml mini-wsa-generator/
RUN mvn -B -q dependency:go-offline
COPY mini-wsa-app/src mini-wsa-app/src
COPY mini-wsa-generator/src mini-wsa-generator/src
RUN mvn -B -q -DskipTests package

# The generator image, used by the "seed" compose profile. Defined before the app so that a plain
# `docker build .` (which builds the last stage) still produces the app.
FROM eclipse-temurin:21-jre AS generator
RUN groupadd --system app && useradd --system --gid app --no-create-home app
WORKDIR /app
COPY --from=build /src/mini-wsa-generator/target/mini-wsa-generator-*.jar generator.jar
USER app
ENTRYPOINT ["java", "-jar", "/app/generator.jar"]

FROM eclipse-temurin:21-jre AS app
RUN groupadd --system app && useradd --system --gid app --no-create-home app
WORKDIR /app
COPY --from=build /src/mini-wsa-app/target/mini-wsa-app-*.jar app.jar
USER app
# Size the heap from the container's memory limit, not the host's.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75"
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
