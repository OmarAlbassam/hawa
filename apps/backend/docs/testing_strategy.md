# Testing Strategy

## Philosophy

We write tests that actually matter. Our tests show exactly how the system works by testing real user flows. We focus on integration and feature-level tests because:

1. **Real confidence** — testing the actual system catches more problems than isolated unit tests with mocks
2. **Tests as documentation** — each test shows a complete feature working
3. **Less maintenance** — no need to update mocks when internal code changes
4. **Behavior over implementation** — test what happens, not how it happens

## Test Architecture

### What We Use

- **Framework**: JUnit 5
- **Assertions**: AssertJ (`assertThat`) for readability
- **HTTP Testing**: MockMvc with `@SpringBootTest` + `@AutoConfigureMockMvc`
- **Database**: H2 in PostgreSQL compatibility mode (Testcontainers later)
- **Mocking**: Mockito — only for external services, never for our own code
- **JSON**: `jsonPath` for response assertions

### Test Types (in order of preference)

| Type | Scope | Annotation | When to Use |
|------|-------|------------|-------------|
| **Feature test** | Full flow across multiple services | `@SpringBootTest` + `@AutoConfigureMockMvc` | Always — this is the default |
| **Service unit test** | Single service with complex logic | `@ExtendWith(MockitoExtension.class)` | Only when business logic is complex enough to warrant isolated testing |
| **Repository test** | Custom queries | `@DataJpaTest` | Only when you write custom `@Query` methods |

Feature tests are the backbone. Unit tests are supplementary.

## Writing Tests

### File Structure

Tests are organized by feature (user flow), not by entity or layer:

```
src/test/java/com/hawa/hawa_backend/
├── HawaBackendApplicationTests.java    # Context load smoke test
├── features/
│   ├── StartAnalysisTest.java          # User requests a new analysis report
│   ├── ViewReportResultsTest.java      # User views completed report with posts/reviews
│   ├── SubmitFeedbackTest.java         # User corrects a misclassified review
│   ├── ManageBrandsTest.java           # CRUD brand + keywords for a company
│   └── ManageUsersTest.java            # Admin manages user accounts
└── helpers/
    └── TestFactory.java                # Factory methods for creating test data
```

Each test file covers a **feature**, not an entity. A feature test may touch multiple entities, services, and endpoints — that's the point.

### Test Naming Guidelines

#### Class Names

Use the feature or user flow being tested:

```java
// Good — clear feature name
class StartAnalysisTest {

// Good — user action
class SubmitFeedbackTest {

// Bad — entity-based
class ReportServiceTest {

// Bad — too generic
class ApiTests {
```

#### Test Method Names

Write names that describe behavior from the user's perspective:

```java
// Good — describes behavior and context
@Test void shouldCreateReportAndCollectPosts_whenAnalysisRequested()
@Test void shouldReturnSentimentBreakdown_whenReportIsCompleted()
@Test void shouldRejectAnalysis_whenBrandHasNoKeywords()
@Test void shouldUpdateReviewScore_whenUserSubmitsFeedback()

// Good — error scenarios
@Test void shouldReturn400_whenRequiredFieldsMissing()
@Test void shouldReturn404_whenBrandDoesNotExist()

// Bad — too vague
@Test void shouldWork()
@Test void shouldCreateReport()

// Bad — testing implementation
@Test void shouldCallRepository()
@Test void shouldSaveToDatabase()

// Bad — unclear context
@Test void shouldReturn201()
@Test void shouldUpdateStatus()
```

#### Nested Classes for Context

Use `@Nested` to group related scenarios:

```java
class StartAnalysisTest {

    @Nested
    class WhenValidRequest {
        @Test void shouldCreateReportWithPendingStatus() { }
        @Test void shouldAssociateReportWithBrandAndUser() { }
    }

    @Nested
    class WhenBrandHasNoKeywords {
        @Test void shouldReturn400WithClearMessage() { }
    }

    @Nested
    class WhenDuplicateReportExists {
        @Test void shouldAllowNewReportForSameBrand() { }
    }
}
```

### Standard Test Format

Every feature test follows this pattern:

```java
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class StartAnalysisTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CompanyRepository companyRepo;

    @Autowired
    private BrandRepository brandRepo;

    @Autowired
    private UserRepository userRepo;

    @Autowired
    private ReportRepository reportRepo;

    // Only mock external services
    @MockBean
    private RedditClient redditClient;

    private Company company;
    private User user;
    private Brand brand;

    @BeforeEach
    void setUp() {
        // Build the scenario — a company with a user and a brand
        company = TestFactory.createCompany(companyRepo);
        user = TestFactory.createUser(userRepo, company);
        brand = TestFactory.createBrand(brandRepo, company);
    }

    @Test
    void shouldCreateReportAndReturnLocation_whenAnalysisRequested() throws Exception {
        // Act — user requests analysis just like the frontend would
        mockMvc.perform(post("/api/reports")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                        "brandId": %d,
                        "userId": %d,
                        "dataSource": "REDDIT",
                        "dateFrom": "2026-01-01",
                        "dateTo": "2026-03-01"
                    }
                    """.formatted(brand.getBrandId(), user.getUserId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.brandId").value(brand.getBrandId()));

        // Verify the full state — report exists with correct relationships
        assertThat(reportRepo.findAll()).hasSize(1);
        Report saved = reportRepo.findAll().getFirst();
        assertThat(saved.getBrand().getBrandId()).isEqualTo(brand.getBrandId());
        assertThat(saved.getStatus()).isEqualTo(ReportStatusEnum.PENDING);
    }
}
```

Key points:
- `@Transactional` rolls back after each test — no manual cleanup needed
- Setup creates the full scenario context in `@BeforeEach`
- Tests hit real endpoints with real database writes
- Assertions check both the HTTP response AND the database state

## Test Data Management

### Factory Class

Use a shared factory with static methods to create realistic test data:

```java
// helpers/TestFactory.java
public final class TestFactory {

    private TestFactory() {}

    public static Company createCompany(CompanyRepository repo) {
        Company company = Company.builder()
                .companyName("Acme Corp")
                .build();
        return repo.save(company);
    }

    public static Company createCompany(CompanyRepository repo, String name) {
        Company company = Company.builder()
                .companyName(name)
                .build();
        return repo.save(company);
    }

    public static User createUser(UserRepository repo, Company company) {
        User user = User.builder()
                .company(company)
                .firstName("Omar")
                .lastName("Test")
                .role(UserRoleEnum.MARKETING_USER)
                .build();
        return repo.save(user);
    }

    public static User createAdmin(UserRepository repo, Company company) {
        User user = User.builder()
                .company(company)
                .firstName("Admin")
                .lastName("User")
                .role(UserRoleEnum.ADMIN)
                .build();
        return repo.save(user);
    }

    public static Brand createBrand(BrandRepository repo, Company company) {
        Brand brand = Brand.builder()
                .brandName("TestBrand")
                .company(company)
                .industry("Technology")
                .build();
        return repo.save(brand);
    }

    public static Brand createBrand(BrandRepository repo, Company company, String name) {
        Brand brand = Brand.builder()
                .brandName(name)
                .company(company)
                .build();
        return repo.save(brand);
    }
}
```

Rules:
- Factory saves to the database and returns the managed entity
- Provide sensible defaults, accept overrides via parameters
- Add new factory methods as features need them — don't pre-build everything

### Real Scenarios

Create test data that tells a story:

```java
@Test
void shouldReturnSentimentBreakdown_whenReportHasReviewedPosts() throws Exception {
    // A completed report with posts that have been reviewed
    Report report = TestFactory.createReport(reportRepo, user, brand, ReportStatusEnum.COMPLETED);
    Post post1 = TestFactory.createPost(postRepo, report, "Great product!", LanguageEnum.EN);
    Post post2 = TestFactory.createPost(postRepo, report, "Terrible service", LanguageEnum.EN);
    TestFactory.createReview(reviewRepo, post1, 4.5, EmotionEnum.JOY, AspectEnum.PRODUCT);
    TestFactory.createReview(reviewRepo, post2, 1.2, EmotionEnum.ANGER, AspectEnum.SERVICE);

    // User views the report results
    mockMvc.perform(get("/api/reports/{id}", report.getReportId()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("COMPLETED"))
            .andExpect(jsonPath("$.score").exists());
}
```

## External Service Mocking

### What to Mock

Only mock what crosses the system boundary — never mock your own code:

```java
// Good — mock external API
@MockBean
private RedditClient redditClient;

// Bad — don't mock your own services
@MockBean
private ReportService reportService;
```

### Test Both Success and Failure

```java
@Nested
class WhenRedditApiAvailable {
    @BeforeEach
    void setUp() {
        when(redditClient.fetchPosts(any())).thenReturn(List.of(
                new RedditPost("Great product!", "http://reddit.com/1"),
                new RedditPost("Bad experience", "http://reddit.com/2")
        ));
    }

    @Test void shouldCollectPostsFromReddit() { }
}

@Nested
class WhenRedditApiFails {
    @BeforeEach
    void setUp() {
        when(redditClient.fetchPosts(any()))
                .thenThrow(new RuntimeException("Reddit API unavailable"));
    }

    @Test void shouldMarkReportAsFailed_whenRedditUnavailable() { }
}
```

## Testing Patterns

### Endpoint Testing

```java
@Test
void shouldCreateBrandWithKeywords_whenValidRequest() throws Exception {
    mockMvc.perform(post("/api/brands")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                    "brandName": "Nike",
                    "companyId": %d,
                    "industry": "Sportswear"
                }
                """.formatted(company.getCompanyId())))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.brandName").value("Nike"))
            .andExpect(jsonPath("$.industry").value("Sportswear"));

    assertThat(brandRepo.findAll()).hasSize(1);
}
```

### Validation Testing

```java
@Test
void shouldReturn400WithFieldErrors_whenRequiredFieldsMissing() throws Exception {
    mockMvc.perform(post("/api/brands")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                    "industry": "Sportswear"
                }
                """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").exists());
}
```

### Pagination Testing

```java
@Test
void shouldReturnPaginatedResults_whenMultipleBrandsExist() throws Exception {
    TestFactory.createBrand(brandRepo, company, "Brand A");
    TestFactory.createBrand(brandRepo, company, "Brand B");
    TestFactory.createBrand(brandRepo, company, "Brand C");

    mockMvc.perform(get("/api/brands")
            .param("page", "0")
            .param("size", "2")
            .param("sort", "brandName,asc"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content").isArray())
            .andExpect(jsonPath("$.content.length()").value(2))
            .andExpect(jsonPath("$.totalElements").value(3))
            .andExpect(jsonPath("$.content[0].brandName").value("Brand A"));
}
```

### Idempotency Testing

```java
@Test
void shouldNotCreateDuplicateFeedback_whenSameUserReviewsCombination() throws Exception {
    // First feedback
    mockMvc.perform(post("/api/feedbacks")
            .contentType(MediaType.APPLICATION_JSON)
            .content(feedbackJson))
            .andExpect(status().isCreated());

    // Same feedback again
    mockMvc.perform(post("/api/feedbacks")
            .contentType(MediaType.APPLICATION_JSON)
            .content(feedbackJson))
            .andExpect(status().isConflict());

    assertThat(feedbackRepo.count()).isEqualTo(1);
}
```

## Picking Test Scenarios

- Write tests for distinct **business scenarios**, not distinct assertions
- Verify multiple aspects of the same scenario in one test (HTTP response + DB state + side effects)
- Keep error scenarios separate from happy paths
- Each test should answer: "What unique user flow am I testing?"
- If tests share identical setup and action, they should probably be one test

Tests have a maintenance cost. The goal: **write the minimum number of tests that give maximum confidence**.

## When to Add Tests

- Adding new features — write the test first (TDD)
- Fixing bugs — write a test that reproduces the bug, then fix it
- Complex business logic that needs examples
- External integration points (Reddit API, LLM service)

## Assertions Cheat Sheet

```java
// Object assertions
assertThat(report).isNotNull();
assertThat(report.getStatus()).isEqualTo(ReportStatusEnum.COMPLETED);

// Collection assertions
assertThat(posts).hasSize(3);
assertThat(posts).extracting(Post::getLanguage).containsOnly(LanguageEnum.EN);

// Exception assertions
assertThatThrownBy(() -> service.getReport(999L))
        .isInstanceOf(ResourceNotFoundException.class)
        .hasMessageContaining("not found");

// JSON path assertions (MockMvc)
.andExpect(jsonPath("$.status").value("PENDING"))
.andExpect(jsonPath("$.content").isArray())
.andExpect(jsonPath("$.content.length()").value(2))
```

## Best Practices

1. **Feature-first** — test user flows, not individual methods
2. **Real database** — always hit the database, never mock repositories
3. **Mock only externals** — Reddit API, LLM service, etc.
4. **`@Transactional` for cleanup** — no manual `deleteAll()` needed
5. **Clear names** — test name should explain the scenario without reading the code
6. **One scenario per test** — but verify everything about that scenario
7. **Factory for data** — never construct entities inline in tests

## Summary

Our testing approach: write feature-level integration tests that show how the system works end-to-end. Each test tells a story about a user flow from HTTP request to database state. We don't write unit tests for every class — we test real features that give us confidence the system works.

A good test replaces pages of documentation.
