# syntax=docker/dockerfile:1
# Build stage: compiles the fat JAR (tests run in CI, not here).
FROM eclipse-temurin:17-jdk-jammy AS build
WORKDIR /src
COPY .mvn/ .mvn
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -q -B -ntp dependency:go-offline
COPY src ./src
RUN ./mvnw -q -B -ntp -DskipTests package

# Runtime stage.
# Ubuntu Jammy (glibc) instead of Alpine so the same image can also run the "demo" profile,
# whose embedded PostgreSQL binaries need glibc. Runs as a non-root user.
FROM eclipse-temurin:17-jre-jammy
RUN groupadd --system --gid 10001 app \
 && useradd --system --uid 10001 --gid app --home-dir /app --shell /usr/sbin/nologin app \
 && mkdir -p /app \
 && chown app:app /app
WORKDIR /app
COPY --from=build --chown=app:app /src/target/forward-api.jar /app/forward-api.jar
USER 10001:10001
# Defaults sized for a 512 MB instance; the platform can override JAVA_TOOL_OPTIONS
# (render.yaml does, for the prod profile). The HTTP port comes from $PORT (default 8080).
ENV TZ=America/Sao_Paulo \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=60 -XX:InitialRAMPercentage=15 -XX:+UseSerialGC -XX:TieredStopAtLevel=1 -XX:MaxMetaspaceSize=160m -XX:ReservedCodeCacheSize=48m -Xss512k -XX:+ExitOnOutOfMemoryError -Djava.io.tmpdir=/tmp"
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/forward-api.jar"]
