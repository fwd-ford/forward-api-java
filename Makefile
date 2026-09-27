.PHONY: run demo build test verify quality package clean ci

## Run the server with an external PostgreSQL (DATABASE_URL), port 8080
run:
	./mvnw spring-boot:run

## Run the self-contained demo: embedded PostgreSQL 16 + seed, port 8080 (no Docker)
demo:
	LOG_FORMAT=CONSOLE ./mvnw spring-boot:run -Dspring-boot.run.profiles=demo

## Build a runnable fat JAR into target/
build:
	./mvnw -B clean package

## Run the test suite (unit + HTTP integration tests on embedded PostgreSQL)
test:
	./mvnw -B test

## Tests + JaCoCo (target/site/jacoco) + Surefire HTML (target/reports/surefire.html)
verify:
	./mvnw -B verify

## Same checks as CI: Spotless, Checkstyle, SpotBugs + FindSecBugs, tests
quality:
	./mvnw -B spotless:check
	./mvnw -B verify -P quality

## Alias for build
package: build

## Remove build artifacts
clean:
	./mvnw clean

## Run all checks (tests + package)
ci: quality package
