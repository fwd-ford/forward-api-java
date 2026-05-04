FROM eclipse-temurin:17-jdk-alpine AS build
WORKDIR /src
COPY .mvn/ .mvn
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -q -B dependency:go-offline
COPY src ./src
RUN ./mvnw -q -B -DskipTests package

FROM eclipse-temurin:17-jre-alpine
RUN apk add --no-cache tzdata && adduser -D -u 10001 app
USER app
COPY --from=build /src/target/forward-api.jar /app/forward-api.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/forward-api.jar"]
