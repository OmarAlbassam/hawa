# Hawa Backend

Spring Boot 4.0.3 REST API (Java 25, Maven).

## Integration Tests (Testcontainers)

Integration tests use [Testcontainers](https://testcontainers.com/) to run a real PostgreSQL 16 instance in Docker. This ensures tests exercise the actual database (custom enum types, Liquibase migrations) instead of H2.

### Prerequisites

- Docker must be running on your machine.

### Container Reuse

The PostgreSQL container is configured with `.withReuse(true)` so it persists across test runs, avoiding cold-start overhead. To enable this, add the following to `~/.testcontainers.properties` (create the file if it doesn't exist):

```properties
testcontainers.reuse.enable=true
```

Without this flag, a new container is created and destroyed on every test run.

### Docker API Version

The project includes `src/test/resources/docker-java.properties` with `api.version=1.44`. This is required because `docker-java:3.4.2` (bundled with Testcontainers 1.21.0) defaults to Docker API version 1.32, which is rejected by Docker Engine 29+ (minimum API 1.40). See [testcontainers-java#11210](https://github.com/testcontainers/testcontainers-java/issues/11210).

### Running

```bash
./mvnw test
```
