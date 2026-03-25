# Hawa Backend

Your goal is to help write a high-quality Spring Boot application by following established best practices.

## Project Setup & Structure

- **Framework:** Spring Boot 4.0.3 REST API with Java 25.
- **Build Tool:** Maven (`pom.xml`). Always use the Maven wrapper (`./mvnw`).
- **Starters:** Uses `spring-boot-starter-webmvc`, `spring-boot-starter-data-jpa`, `spring-boot-starter-validation`.
- **Migrations:** Flyway — migration files at `src/main/resources/db/migration/V{N}__{description}.sql`.
- **Lombok:** Enabled — use for boilerplate reduction (`@Data`, `@Builder`, `@AllArgsConstructor`, etc.).
- **Package Structure:** Organize code by feature/entity (e.g., `com.hawa.hawa_backend.user`, `com.hawa.hawa_backend.order`) rather than by layer. Each entity gets its own package:

```
com.hawa.hawa_backend/
├── user/
│   ├── User.java             # @Entity
│   ├── UserController.java   # @RestController
│   ├── UserService.java      # @Service
│   ├── UserRepository.java   # @Repository
│   ├── UserMapper.java       # Plain utility class, static methods
│   └── dto/
│       ├── CreateUserRequest.java
│       ├── UpdateUserRequest.java
│       └── UserResponse.java
├── order/
│   ├── Order.java
│   ├── OrderController.java
│   ├── OrderService.java
│   └── ...
├── config/                   # Shared configuration (@Configuration)
├── exception/                # Global exception handling (@ControllerAdvice)
└── util/                     # Shared utilities
```

## Build & Run

```bash
./mvnw spring-boot:run            # Run the app (port 8080)
./mvnw test                       # Run tests
./mvnw clean package              # Build JAR
./mvnw clean package -DskipTests  # Build without tests
```

## Dependency Injection & Components

- **Constructor Injection:** Always use constructor-based injection for required dependencies. This makes components easier to test and dependencies explicit.
- **Immutability:** Declare dependency fields as `private final`.
- **Component Stereotypes:** Use `@Service`, `@Repository`, and `@RestController` annotations appropriately to define beans.

## Configuration

- **Externalized Configuration:** Use `application.properties` (or `application.yml`) for configuration.
- **Type-Safe Properties:** Use `@ConfigurationProperties` to bind configuration to strongly-typed Java objects.
- **Profiles:** Use Spring Profiles (`application-dev.properties`, `application-prod.properties`) to manage environment-specific configurations.
- **Secrets Management:** Do not hardcode secrets. Use environment variables or a dedicated secret management tool.
- **Database:** PostgreSQL for production, H2 for development/testing.
- **Frontend Origin:** CORS configured for `http://localhost:5173` (Vite frontend).

## Web Layer (Controllers)

- **RESTful APIs:** Design clear and consistent RESTful endpoints under the `/api/` prefix.
- **HTTP Methods:** Use proper HTTP methods: GET (read), POST (create), PUT (update), DELETE (delete).
- **Pagination:** All list endpoints accept `Pageable` and return `Page<Response>`. Spring resolves `?page=0&size=20&sort=createdAt,desc` automatically.
- **DTOs (Data Transfer Objects):** Use DTOs to expose and consume data in the API layer. Do not expose JPA entities directly to the client.
- **Validation:** Use Java Bean Validation (JSR 380) with annotations (`@Valid`, `@NotNull`, `@Size`) on DTOs to validate request payloads.
- **Error Handling:** Implement a global exception handler using `@ControllerAdvice` and `@ExceptionHandler` to provide consistent error responses.

## Service Layer

- **Business Logic:** Encapsulate all business logic within `@Service` classes. Controllers stay thin.
- **Statelessness:** Services should be stateless.
- **Transaction Management:** Use `@Transactional` on service methods to manage database transactions declaratively. Apply it at the most granular level necessary.

## Data Layer (Repositories)

- **Spring Data JPA:** Use Spring Data JPA repositories by extending `JpaRepository<Entity, ID>` for standard database operations.
- **Custom Queries:** For complex queries, use `@Query` or the JPA Criteria API.
- **Projections:** Use DTO projections to fetch only the necessary data from the database.
- **Optional Handling:** Use `Optional` returns from repositories, handle properly (no `.get()` without check).
- **Entity Annotations:** Use `@Entity` with `@Table(name = "...")`. IDs are `Long` with `@GeneratedValue(strategy = GenerationType.IDENTITY)`. Column constraints via `@Column(nullable = false, length = ...)`.
- **PostgreSQL Compatibility:** Write SQL-compatible code for the production database.
- **Schema Reference:** See `docs/database_schema.md` for the full schema, entity relationships, and enum types.

## Logging

- **SLF4J:** Use the SLF4J API for logging (Lombok `@Slf4j` annotation preferred).
- **Parameterized Logging:** Use parameterized messages (`log.info("Processing user {}...", userId);`) instead of string concatenation.

## Testing

- **Unit Tests:** Write unit tests for services and components using JUnit 5 and Mockito.
- **Integration Tests:** Use `@SpringBootTest` for integration tests that load the Spring application context.
- **Test Slices:** Use `@WebMvcTest` for controller tests, `@DataJpaTest` for repository tests.
- **Naming:** Use meaningful test method names: `shouldDoX_whenY`.
- **Testcontainers:** Consider using Testcontainers for reliable integration tests with real databases.

## Security

- **Spring Security:** Use Spring Security for authentication and authorization when implemented.
- **Password Encoding:** Always encode passwords using a strong hashing algorithm like BCrypt.
- **Input Sanitization:** Prevent SQL injection by using Spring Data JPA or parameterized queries. Prevent XSS by properly encoding output.
