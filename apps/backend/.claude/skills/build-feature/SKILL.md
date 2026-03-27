---
name: build-feature
description: Build a complete backend feature in a Spring Boot monorepo — from database schema to working API. Use this skill whenever the user asks to build, add, create, or implement a backend feature, endpoint, entity, API, or CRUD resource. Also triggers on "add X to the backend", "create an endpoint for Y", "build the Z module", or any request that involves generating Spring Boot code for a new feature. Always use this skill even if the feature sounds simple — it enforces consistent patterns.
---

# Build Feature Backend

You are building a feature for a Spring Boot 4 REST API (Java 25, Maven, PostgreSQL, Lombok). The codebase follows a feature-based package structure under `com.hawa.hawa_backend`.

## Step 0: Understand the Feature

First, read `apps/backend/docs/database_schema.md` to understand the existing schema, entity relationships, and enum types. Use this as context for how the new feature fits into the existing data model.

Read the feature request. **Always ask at least 2 clarifying questions** before moving to the plan — even if the request seems clear, there are always details worth confirming (edge cases, scope boundaries, behavior on error, etc.). If you have more than 2 questions, ask them all. Questions are encouraged — it's better to ask too many than to assume wrong.

Example areas to ask about:

1. **What entities are involved?** (e.g., "Brand", "SentimentReport")
2. **What endpoints are needed?** (e.g., CRUD? Or specific operations only?)
3. **What are the relationships?** (e.g., Brand has many SentimentReports)
4. **Any special requirements?** (pagination, filtering, file upload, external API calls, etc.)
5. **Edge cases?** (what happens on duplicates, empty input, missing references, etc.)

Iterate with the user until you're confident you understand the feature fully. Then move to the plan.

Once you fully understand the feature, **enter plan mode** and present:

1. **The implementation plan** — steps you will take, files you will create/modify
2. **Frontend Integration Spec** — if this feature adds or changes any API endpoints, include a spec so the frontend team knows what's coming. If the feature is purely internal (refactor, index, backend-only bug fix), write: "No frontend changes required."

### Frontend Integration Spec format

Keep it engineer-to-engineer. No fluff. Use this structure (include only sections that apply):

```
### Frontend Integration Spec

#### New Endpoints

POST /api/brands
  Body: { name: string, description?: string }
  Response (201): { id: number, name: string, description: string | null, createdAt: string }

GET /api/brands?page=0&size=20&sort=createdAt,desc
  Response (200): Page<BrandResponse>

GET /api/brands/{id}
  Response (200): BrandResponse
  Errors: 404

DELETE /api/brands/{id}
  Response (204): no body
  Errors: 404

#### Changed Endpoints
(show only what changed)

GET /api/reports/{id}
  Response: added field `brandName: string`

#### Types

type BrandResponse = {
  id: number
  name: string
  description: string | null
  createdAt: string
}

type CreateBrandRequest = {
  name: string
  description?: string
}

#### Notes
(anything non-obvious: enum values, auth, specific error codes, etc.)
```

Rules for the spec:
- TypeScript-style types (frontend is React + TypeScript)
- Use actual field names from your DTOs
- For enums, list all values: `status: "PENDING" | "PROCESSING" | "COMPLETED" | "FAILED"`
- Nullable fields: `type | null`. Optional request fields: `field?: type`
- Paginated endpoints return: `{ content: T[], totalElements: number, totalPages: number, number: number, size: number }`
- Include error status codes the frontend should handle

Wait for the user to approve the plan before writing any code.

## Step 1: Tests

Before writing any implementation code, use the `write-tests` skill to write tests for this feature. Think about what the feature should do from the outside — what endpoints exist, what responses they return, what errors they produce — and encode that as tests first.

Write the structural code needed for tests to compile (Schema → Entity → DTOs → Mapper → Repository), then write the tests. The tests will fail — that's expected. You will make them pass by implementing the Service and Controller in later steps.

## Step 2: Database Schema

Think database-first. Before writing any Java code, define the schema:

- Write a Liquibase SQL changeset file at `src/main/resources/db/changelog/db.changelog-{N}-{description}.sql`
- Include the `-- liquibase formatted sql` header and `-- changeset hawa:{N}-{description}` tag
- Register the new file in `db.changelog-master.xml` via an `<include file="..."/>` element
- Use the next changeset number in sequence (check existing changelog files)
- Define table names (snake_case, plural: `sentiment_reports`)
- Define columns, types, constraints, foreign keys
- Consider indexes for columns that will be queried/filtered frequently

## Step 3: Entity

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

## Step 4: DTOs

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

## Step 5: Mapper

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

## Step 6: Repository

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

## Step 7: Service

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

## Step 8: Controller

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

- [ ] Plan approved by user (with Frontend Integration Spec if applicable)
- [ ] SQL migration/schema defined
- [ ] Entity maps to schema correctly
- [ ] All three DTOs created with validation
- [ ] Mapper handles create, update, and response conversions
- [ ] Repository has only the queries needed
- [ ] Service uses `@Transactional` correctly
- [ ] Controller returns correct HTTP status codes
- [ ] `@Valid` on all request body parameters
- [ ] No entity exposed directly in API responses
- [ ] Tests written first and passing
- [ ] Existing exception handler reused (or created if missing)