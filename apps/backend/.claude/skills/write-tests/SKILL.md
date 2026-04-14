---
name: write-tests
description: Write feature-level integration tests for the Spring Boot backend. Use this skill when the user asks to write tests, add tests, test a feature, or ensure a feature works. Also triggers on "test this", "add tests for X", "write tests", or any request involving test creation. Always read docs/testing_strategy.md first.
---

# Write Tests

You are writing tests for a Spring Boot 4 REST API (Java 25, Maven, H2 for tests). The codebase follows a feature-based testing strategy — tests verify user flows, not individual classes.

## Step 0: Read the Testing Strategy

First, read `apps/backend/docs/testing_strategy.md` to understand the philosophy, patterns, and conventions. Everything in this skill follows that strategy.

Then, understand what you're testing:

1. **What feature/user flow is being tested?** (e.g., "start analysis", "manage brands")
2. **What endpoints are involved?**
3. **What entities participate in this flow?**
4. **Are there external services to mock?** (Reddit API, LLM service)

## Step 1: Identify Test Scenarios

Before writing any code, list the scenarios to test:

- **Happy path** — the feature works as expected
- **Validation errors** — required fields missing, invalid values
- **Not found** — referenced entities don't exist
- **Edge cases** — empty lists, pagination boundaries, duplicates
- **External failures** — mocked external services returning errors

Pick scenarios that cover distinct business behaviors. Don't write separate tests for things that can be verified in a single scenario.

## Step 2: Create or Update TestFactory

Check if `src/test/java/com/hawa/hawa_backend/helpers/TestFactory.java` exists. If not, create it. If it exists, add any new factory methods needed for your test scenarios.

```
src/test/java/com/hawa/hawa_backend/helpers/
└── TestFactory.java
```

Rules:
- Static methods that save to the database and return the managed entity
- Provide sensible defaults, accept overrides via method parameters
- Add only what the current test needs — don't pre-build unused factories

## Step 3: Write the Test Class

Create the test file under the `features` package:

```
src/test/java/com/hawa/hawa_backend/features/
└── {FeatureName}Test.java
```

### Class Structure

```java
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class {FeatureName}Test {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private {needed repositories};

    // Only mock external services
    @MockBean
    private {ExternalClient} externalClient;

    // Shared test state
    private Company company;
    private User user;

    @BeforeEach
    void setUp() {
        // Build the scenario context
        company = TestFactory.createCompany(companyRepo);
        user = TestFactory.createUser(userRepo, company);
    }
}
```

### Test Methods

```java
@Test
void shouldDoExpectedThing_whenScenarioCondition() throws Exception {
    // Setup — additional data specific to this scenario
    Brand brand = TestFactory.createBrand(brandRepo, company);

    // Act — call the endpoint like the frontend would
    mockMvc.perform(post("/api/reports")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                { "brandId": %d, "userId": %d }
                """.formatted(brand.getBrandId(), user.getUserId())))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("PENDING"));

    // Assert — verify database state
    assertThat(reportRepo.findAll()).hasSize(1);
}
```

### Grouping with @Nested

Use `@Nested` to group related scenarios:

```java
@Nested
class WhenValidRequest {
    @Test void shouldCreateResourceAndReturn201() { }
    @Test void shouldPersistWithCorrectRelationships() { }
}

@Nested
class WhenInvalidRequest {
    @Test void shouldReturn400_whenRequiredFieldsMissing() { }
    @Test void shouldReturn404_whenReferencedEntityNotFound() { }
}
```

## Step 4: Run and Verify

Run the tests:

```bash
cd apps/backend && ./mvnw test -pl . -Dtest="{FeatureName}Test"
```

Or run all tests:

```bash
cd apps/backend && ./mvnw test
```

All tests must pass before you're done.

## Checklist Before Finishing

- [ ] Test file is under `features/` package, named after the feature (not the entity)
- [ ] Uses `@SpringBootTest` + `@AutoConfigureMockMvc` + `@ActiveProfiles("test")` + `@Transactional`
- [ ] Test data created via `TestFactory`, not inline construction
- [ ] Only external services are mocked (`@MockBean`), never internal code
- [ ] Assertions use AssertJ (`assertThat`) and `jsonPath`
- [ ] Test names describe behavior: `shouldX_whenY`
- [ ] Happy path, validation errors, and not-found scenarios covered
- [ ] All tests pass
- [ ] Logging doesn't leak sensitive data (no passwords, tokens, or secrets in log statements — see `CLAUDE.md § Sensitive Data Rules`)

## What NOT to Do

- Don't mock repositories or services — hit the real database
- Don't write a test class per entity — write per feature
- Don't test that a method was called — test what happened
- Don't create test data inline — use TestFactory
- Don't manually clean the database — `@Transactional` handles rollback
- Don't test Lombok getters/setters or JPA boilerplate
