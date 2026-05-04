.PHONY: run build test package clean ci

## Run the server locally (port 8080 by default)
run:
	./mvnw spring-boot:run

## Build a runnable fat JAR into target/
build:
	./mvnw -B clean package

## Run the test suite
test:
	./mvnw -B test

## Alias for build
package: build

## Remove build artifacts
clean:
	./mvnw clean

## Run all checks (tests + package)
ci: test package
