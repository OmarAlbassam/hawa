---
name: build-feature
description: Build a complete backend feature in a Spring Boot monorepo — from database schema to working API. Use this skill whenever the user asks to build, add, create, or implement a backend feature, endpoint, entity, API, or CRUD resource. Also triggers on "add X to the backend", "create an endpoint for Y", "build the Z module", or any request that involves generating Spring Boot code for a new feature. Always use this skill even if the feature sounds simple — it enforces consistent patterns.
---

# Build Feature Backend

You are building a feature for a Spring Boot 4 REST API (Java 25, Maven, PostgreSQL, Lombok). The codebase follows a feature-based package structure under `com.hawa.hawa_backend`.

## Step 0: Understand the Feature

First, read `apps/backend/docs/database_schema.md` to understand the existing schema, entity relationships, and enum types. Use this as context for how the new feature fits into the existing data model.

Read the feature request. If any of the following are unclear, ask before writing code:

1. **What entities are involved?** (e.g., "Brand", "SentimentReport")
2. **What endpoints are needed?** (e.g., CRUD? Or specific operations only?)
3. **What are the relationships?** (e.g., Brand has many SentimentReports)
4. **Are tests needed?** If yes, you will follow TDD — write tests first, then implement.
5. **Any special requirements?** (pagination, filtering, file upload, external API calls, etc.)

If the description is clear and complete, skip the questions and start building.

## Step 1: Database Schema

Think database-first. Before writing any Java code, define the schema:

- Write a Flyway migration file at `src/main/resources/db/migration/V{N}__{description}.sql`
- Use the next version number in sequence (check existing migrations)
- Define table names (snake_case, plural: `sentiment_reports`)
- Define columns, types, constraints, foreign keys
- Consider indexes for columns that will be queried/filtered frequently

## Step 2: Entity

Create the JPA entity that maps to the schema from Step 1.

```
com.hawa.hawa_backend.{feature}/
└── {Feature}.java
```

Rules:
- `@Entity` + `@Table(name = "...")`
- `@Id` + `@GeneratedValue(strategy = GenerationType.IDENTITY)`
- `@Column` with explicit constraints (`nullable`, `length`, `unique` where appropriate)
- Use Lombok: `@Data`, `@Builder`, `@NoArgsConstructor`, `@AllArgsConstructor`
- Relationship annotations (`@ManyToOne`, `@OneToMany`, etc.) with explicit `fetch` and `cascade` types — never rely on defaults
- `@JoinColumn(name = "...")` on owning side
- Timestamps: use `@CreatedDate` / `@LastModifiedDate` with `@EntityListeners(AuditingEntityListener.class)` if the entity needs created/updated tracking

## Step 3: DTOs

Every entity gets three DTOs:

### `Create{Feature}Request`
- Only fields needed to create the entity
- Validation annotations: `@NotNull`, `@NotBlank`, `@Size`, `@Email`, etc.
- No ID field — the server generates it
- Foreign keys referenced by ID (e.g., `Long brandId`, not a nested object)

### `Update{Feature}Request`
- Only fields allowed to be updated
- All fields nullable (partial updates) unless business logic requires otherwise
- Same validation annotations where applicable

### `{Feature}Response`
- What the API returns to the client
- Never expose sensitive fields (passwords, internal flags)
- Include related entity info as needed (e.g., `String brandName` instead of full nested object, unless the client needs it)
- Include `id`, `createdAt`, `updatedAt` if the entity tracks them

Place all three in:
```
com.hawa.hawa_backend.{feature}/dto/
├── Create{Feature}Request.java
├── Update{Feature}Request.java
└── {Feature}Response.java
```

## Step 4: Mapper

Create `{Feature}Mapper.java` — a simple utility class that converts between Entity ↔ DTOs.

```
com.hawa.hawa_backend.{feature}/
└── {Feature}Mapper.java
```

Rules:
- Plain utility class with `private` constructor and `static` methods — no `@Component`, no Spring involvement
- Methods: `toEntity(Create{Feature}Request dto)`, `toResponse({Feature} entity)`
- For updates: `updateEntity({Feature} entity, Update{Feature}Request dto)` — only update non-null fields
- Do NOT use MapStruct or any mapping library — write the mapping by hand. It's 10 lines of code and stays readable.

## Step 5: Repository

Create `{Feature}Repository.java` extending `JpaRepository<{Feature}, Long>`.

```
com.hawa.hawa_backend.{feature}/
└── {Feature}Repository.java
```

Rules:
- Add custom query methods only if needed by the feature requirements
- Use Spring Data method naming for simple queries (`findByBrandId`, `existsByEmail`)
- Use `@Query` for anything more complex
- Return `Optional<{Feature}>` for single-entity lookups

## Step 6: Service

Create `{Feature}Service.java` with `@Service`.

```
com.hawa.hawa_backend.{feature}/
└── {Feature}Service.java
```

Rules:
- Constructor injection, `private final` dependencies
- `@Transactional` on write methods
- `@Transactional(readOnly = true)` on read methods
- List methods accept `Pageable` and return `Page<{Feature}Response>` — map entity pages using `page.map(Mapper::toResponse)`
- Throw meaningful exceptions (e.g., `ResourceNotFoundException`) — never return null from a "get" method
- Use the Mapper for all Entity ↔ DTO conversions
- Accept request DTOs (`Create{Feature}Request`, `Update{Feature}Request`) as input, return `{Feature}Response` DTOs as output — never return entities from service methods
- Logging with `@Slf4j`: log at `info` for operations, `warn` for business rule violations, `error` for unexpected failures

## Step 7: Controller

Create `{Feature}Controller.java` with `@RestController`.

```
com.hawa.hawa_backend.{feature}/
└── {Feature}Controller.java
```

Rules:
- `@RequestMapping("/api/{features}")` (plural, kebab-case)
- Thin controllers — delegate everything to the service
- Use `@Valid` on request body parameters
- Return `ResponseEntity<>` with appropriate HTTP status codes:
  - `201 Created` for POST (with `URI` location header if practical)
  - `200 OK` for GET, PUT
  - `204 No Content` for DELETE
- Use `@PathVariable` for resource IDs, `@RequestParam` for filters
- **Pagination:** All list endpoints must accept `Pageable` and return `Page<{Feature}Response>`. Spring resolves `?page=0&size=20&sort=createdAt,desc` automatically — no manual parsing needed

## Step 8: Tests (if requested)

If TDD was requested, write tests BEFORE implementation (Steps 5-7). The order becomes:

1. Schema → Entity → DTOs → Mapper (these are structural, write them first)
2. Write Service tests → Implement Service
3. Write Controller tests → Implement Controller

### Service Tests
- JUnit 5 + Mockito
- Mock the repository
- Test: create, getById (found + not found), update, delete
- Name: `shouldReturnReport_whenIdExists`, `shouldThrowException_whenIdNotFound`

### Controller Tests (optional, include if the feature has non-trivial endpoint logic)
- `@WebMvcTest({Feature}Controller.class)`
- Mock the service
- Test request validation, HTTP status codes, response structure

### Repository Tests (only if custom queries exist)
- `@DataJpaTest`
- Test custom `@Query` methods or complex derived queries

## File Structure Summary

When done, the feature package should look like:

```
com.hawa.hawa_backend.{feature}/
├── {Feature}.java                          # Entity
├── {Feature}Controller.java                # REST Controller
├── {Feature}Service.java                   # Business Logic
├── {Feature}Repository.java                # Data Access
├── {Feature}Mapper.java                    # Entity ↔ DTO mapping
└── dto/
    ├── Create{Feature}Request.java         # Creation input
    ├── Update{Feature}Request.java         # Update input
    └── {Feature}Response.java              # API output
```

## Exception Handling

If the project doesn't already have a global exception handler, create one:

```
com.hawa.hawa_backend.exception/
├── ResourceNotFoundException.java          # 404
├── BadRequestException.java                # 400
├── ApiError.java                           # Standard error response body
└── GlobalExceptionHandler.java             # @ControllerAdvice
```

`ApiError` shape:
```java
{
    "status": 404,
    "message": "Brand not found with id: 5",
    "timestamp": "2026-03-25T10:30:00"
}
```

If these already exist, use them — don't recreate.

## Checklist Before Finishing

- [ ] SQL migration/schema defined
- [ ] Entity maps to schema correctly
- [ ] All three DTOs created with validation
- [ ] Mapper handles create, update, and response conversions
- [ ] Repository has only the queries needed
- [ ] Service uses `@Transactional` correctly
- [ ] Controller returns correct HTTP status codes
- [ ] `@Valid` on all request body parameters
- [ ] No entity exposed directly in API responses
- [ ] Tests written (if requested)
- [ ] Existing exception handler reused (or created if missing)