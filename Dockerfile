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
# Ubuntu Jammy (glibc) instead of Alpine: with SPRING_PROFILES_ACTIVE=demo the API starts
# an embedded PostgreSQL 16 whose bundled binaries need glibc. Runs as a non-root user
# (PostgreSQL refuses to run as root anyway).
FROM eclipse-temurin:17-jre-jammy
RUN groupadd --system --gid 10001 app \
 && useradd --system --uid 10001 --gid app --home-dir /app --shell /usr/sbin/nologin app \
 && mkdir -p /app \
 && chown app:app /app
WORKDIR /app
COPY --from=build --chown=app:app /src/target/forward-api.jar /app/forward-api.jar
USER 10001:10001
ENV TZ=America/Sao_Paulo
EXPOSE 8080
# Sized for a 512 MB machine shared with the embedded PostgreSQL (demo profile):
# ~230 MB max heap, small metaspace/code cache, serial GC, C1-only JIT for fast boot.
ENTRYPOINT ["java", \
  "-XX:MaxRAMPercentage=45", \
  "-XX:InitialRAMPercentage=15", \
  "-XX:+UseSerialGC", \
  "-XX:MaxMetaspaceSize=160m", \
  "-XX:ReservedCodeCacheSize=64m", \
  "-XX:TieredStopAtLevel=1", \
  "-Xss512k", \
  "-XX:+ExitOnOutOfMemoryError", \
  "-Djava.io.tmpdir=/tmp", \
  "-jar", "/app/forward-api.jar"]
