package com.hawa.hawa_backend.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;

import com.hawa.hawa_backend.auth.JwtService;
import com.hawa.hawa_backend.auth.RefreshTokenRepository;
import com.hawa.hawa_backend.brand.Brand;
import com.hawa.hawa_backend.brand.BrandRepository;
import com.hawa.hawa_backend.company.Company;
import com.hawa.hawa_backend.company.CompanyRepository;
import com.hawa.hawa_backend.enums.AspectEnum;
import com.hawa.hawa_backend.enums.DataSourceEnum;
import com.hawa.hawa_backend.enums.EmotionEnum;
import com.hawa.hawa_backend.enums.LanguageEnum;
import com.hawa.hawa_backend.enums.ReportStatusEnum;
import com.hawa.hawa_backend.enums.UserRoleEnum;
import com.hawa.hawa_backend.feedback.Feedback;
import com.hawa.hawa_backend.feedback.FeedbackRepository;
import com.hawa.hawa_backend.post.Post;
import com.hawa.hawa_backend.post.PostRepository;
import com.hawa.hawa_backend.report.Report;
import com.hawa.hawa_backend.report.ReportRepository;
import com.hawa.hawa_backend.review.Review;
import com.hawa.hawa_backend.review.ReviewRepository;
import com.hawa.hawa_backend.user.User;
import com.hawa.hawa_backend.user.UserRepository;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.jdbc.core.JdbcTemplate;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.hawa.hawa_backend.TestcontainersConfiguration;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class AdminControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private CompanyRepository companyRepository;
    @Autowired private RefreshTokenRepository refreshTokenRepository;
    @Autowired private ReportRepository reportRepository;
    @Autowired private PostRepository postRepository;
    @Autowired private FeedbackRepository feedbackRepository;
    @Autowired private BrandRepository brandRepository;
    @Autowired private ReviewRepository reviewRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JwtService jwtService;
    @Autowired private JdbcTemplate jdbcTemplate;

    private Long companyId;
    private User adminUser;
    private String adminToken;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("DELETE FROM feedback");
        jdbcTemplate.execute("DELETE FROM review");
        jdbcTemplate.execute("DELETE FROM post");
        jdbcTemplate.execute("DELETE FROM report");
        jdbcTemplate.execute("DELETE FROM brand");
        jdbcTemplate.execute("DELETE FROM refresh_token");
        jdbcTemplate.execute("DELETE FROM \"user\"");
        jdbcTemplate.execute("DELETE FROM company");

        Company company = new Company();
        company.setCompanyName("Test Corp");
        companyId = companyRepository.save(company).getCompanyId();

        adminUser = createUserDirectly("admin@example.com", "Password1", UserRoleEnum.ADMIN, null);
        adminToken = jwtService.generateAccessToken(adminUser);
    }

    // ==================== User Management ====================

    @Nested
    class UserManagement {

        @Test
        void shouldCreateUser_whenAdmin() throws Exception {
            mockMvc.perform(post("/api/admin/users")
                            .header("Authorization", "Bearer " + adminToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(createUserJson("jane@example.com", "Password1",
                                    companyId, "MARKETING_USER")))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.userId").isNumber())
                    .andExpect(jsonPath("$.firstName").value("Jane"))
                    .andExpect(jsonPath("$.lastName").value("Doe"))
                    .andExpect(jsonPath("$.email").value("jane@example.com"))
                    .andExpect(jsonPath("$.role").value("MARKETING_USER"))
                    .andExpect(jsonPath("$.company.companyId").value(companyId))
                    .andExpect(jsonPath("$.company.companyName").value("Test Corp"))
                    .andExpect(jsonPath("$.createdAt").isNotEmpty())
                    .andExpect(jsonPath("$.updatedAt").isNotEmpty());
        }

        @Test
        void shouldReject403_whenNonAdminCreatesUser() throws Exception {
            User marketingUser = createUserDirectly("marketing@example.com", "Password1",
                    UserRoleEnum.MARKETING_USER, companyId);
            String marketingToken = jwtService.generateAccessToken(marketingUser);

            mockMvc.perform(post("/api/admin/users")
                            .header("Authorization", "Bearer " + marketingToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(createUserJson("new@example.com", "Password1",
                                    companyId, "MARKETING_USER")))
                    .andExpect(status().isForbidden());
        }

        @Test
        void shouldReject401_whenUnauthenticatedCreatesUser() throws Exception {
            mockMvc.perform(post("/api/admin/users")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(createUserJson("new@example.com", "Password1",
                                    companyId, "MARKETING_USER")))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        void shouldReject409_whenCreatingUserWithDuplicateEmail() throws Exception {
            createUserDirectly("dup@example.com", "Password1",
                    UserRoleEnum.MARKETING_USER, companyId);

            mockMvc.perform(post("/api/admin/users")
                            .header("Authorization", "Bearer " + adminToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(createUserJson("dup@example.com", "Password1",
                                    companyId, "MARKETING_USER")))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.message").value("Email already registered"));
        }

        @Test
        void shouldReject404_whenCreatingUserWithInvalidCompanyId() throws Exception {
            mockMvc.perform(post("/api/admin/users")
                            .header("Authorization", "Bearer " + adminToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(createUserJson("new@example.com", "Password1",
                                    99999L, "MARKETING_USER")))
                    .andExpect(status().isNotFound());
        }

        @Test
        void shouldReject400_whenCreatingUserWithWeakPassword() throws Exception {
            mockMvc.perform(post("/api/admin/users")
                            .header("Authorization", "Bearer " + adminToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(createUserJson("new@example.com", "weak",
                                    companyId, "MARKETING_USER")))
                    .andExpect(status().isBadRequest());
        }

        @Test
        void shouldCreateAdmin_withNullCompany() throws Exception {
            mockMvc.perform(post("/api/admin/users")
                            .header("Authorization", "Bearer " + adminToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(createUserJson("newadmin@example.com", "Password1",
                                    null, "ADMIN")))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.role").value("ADMIN"))
                    .andExpect(jsonPath("$.company").isEmpty());
        }

        @Test
        void shouldReject400_whenCreatingAdminWithCompanyId() throws Exception {
            mockMvc.perform(post("/api/admin/users")
                            .header("Authorization", "Bearer " + adminToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(createUserJson("badadmin@example.com", "Password1",
                                    companyId, "ADMIN")))
                    .andExpect(status().isBadRequest());
        }

        @Test
        void shouldReject400_whenCreatingMarketingUserWithoutCompany() throws Exception {
            mockMvc.perform(post("/api/admin/users")
                            .header("Authorization", "Bearer " + adminToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(createUserJson("nocompany@example.com", "Password1",
                                    null, "MARKETING_USER")))
                    .andExpect(status().isBadRequest());
        }

        @Test
        void shouldGetSingleUser_whenAdmin() throws Exception {
            User user = createUserDirectly("target@example.com", "Password1",
                    UserRoleEnum.MARKETING_USER, companyId);

            mockMvc.perform(get("/api/admin/users/" + user.getUserId())
                            .header("Authorization", "Bearer " + adminToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.userId").value(user.getUserId()))
                    .andExpect(jsonPath("$.email").value("target@example.com"))
                    .andExpect(jsonPath("$.company.companyName").value("Test Corp"));
        }

        @Test
        void shouldReject404_whenGettingNonExistentUser() throws Exception {
            mockMvc.perform(get("/api/admin/users/99999")
                            .header("Authorization", "Bearer " + adminToken))
                    .andExpect(status().isNotFound());
        }

        @Test
        void shouldListUsers_paginated_whenAdmin() throws Exception {
            createUserDirectly("user1@example.com", "Password1",
                    UserRoleEnum.MARKETING_USER, companyId);
            createUserDirectly("user2@example.com", "Password1",
                    UserRoleEnum.MARKETING_USER, companyId);

            mockMvc.perform(get("/api/admin/users")
                            .param("page", "0")
                            .param("size", "10")
                            .header("Authorization", "Bearer " + adminToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalElements").value(3)) // admin + 2 users
                    .andExpect(jsonPath("$.content").isArray())
                    .andExpect(jsonPath("$.content.length()").value(3));
        }

        @Test
        void shouldFilterUsersByRole() throws Exception {
            createUserDirectly("marketing1@example.com", "Password1",
                    UserRoleEnum.MARKETING_USER, companyId);

            mockMvc.perform(get("/api/admin/users")
                            .param("role", "MARKETING_USER")
                            .header("Authorization", "Bearer " + adminToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalElements").value(1))
                    .andExpect(jsonPath("$.content[0].role").value("MARKETING_USER"));
        }

        @Test
        void shouldFilterUsersByCompany() throws Exception {
            Company otherCompany = new Company();
            otherCompany.setCompanyName("Other Corp");
            Long otherCompanyId = companyRepository.save(otherCompany).getCompanyId();

            createUserDirectly("other@example.com", "Password1",
                    UserRoleEnum.MARKETING_USER, otherCompanyId);

            mockMvc.perform(get("/api/admin/users")
                            .param("companyId", otherCompanyId.toString())
                            .header("Authorization", "Bearer " + adminToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalElements").value(1))
                    .andExpect(jsonPath("$.content[0].email").value("other@example.com"));
        }

        @Test
        void shouldSearchUsersByEmail() throws Exception {
            createUserDirectly("alice@example.com", "Password1",
                    UserRoleEnum.MARKETING_USER, companyId);

            mockMvc.perform(get("/api/admin/users")
                            .param("search", "alice")
                            .header("Authorization", "Bearer " + adminToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalElements").value(1))
                    .andExpect(jsonPath("$.content[0].email").value("alice@example.com"));
        }

        @Test
        void shouldUpdateUser_whenAdmin() throws Exception {
            User user = createUserDirectly("update@example.com", "Password1",
                    UserRoleEnum.MARKETING_USER, companyId);

            mockMvc.perform(put("/api/admin/users/" + user.getUserId())
                            .header("Authorization", "Bearer " + adminToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                        "firstName": "Updated",
                                        "lastName": "Name",
                                        "email": "updated@example.com"
                                    }
                                    """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.firstName").value("Updated"))
                    .andExpect(jsonPath("$.lastName").value("Name"))
                    .andExpect(jsonPath("$.email").value("updated@example.com"))
                    .andExpect(jsonPath("$.role").value("MARKETING_USER")); // unchanged
        }

        @Test
        void shouldUpdateUserRole_whenAdmin() throws Exception {
            User user = createUserDirectly("role@example.com", "Password1",
                    UserRoleEnum.MARKETING_USER, companyId);

            mockMvc.perform(put("/api/admin/users/" + user.getUserId())
                            .header("Authorization", "Bearer " + adminToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                        "firstName": "Jane",
                                        "lastName": "Doe",
                                        "email": "role@example.com",
                                        "role": "ADMIN"
                                    }
                                    """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.role").value("ADMIN"));
        }

        @Test
        void shouldReject409_whenUpdatingToExistingEmail() throws Exception {
            createUserDirectly("existing@example.com", "Password1",
                    UserRoleEnum.MARKETING_USER, companyId);
            User user = createUserDirectly("change@example.com", "Password1",
                    UserRoleEnum.MARKETING_USER, companyId);

            mockMvc.perform(put("/api/admin/users/" + user.getUserId())
                            .header("Authorization", "Bearer " + adminToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                        "firstName": "Jane",
                                        "lastName": "Doe",
                                        "email": "existing@example.com"
                                    }
                                    """))
                    .andExpect(status().isConflict());
        }

        @Test
        void shouldDeleteUser_whenAdmin() throws Exception {
            User user = createUserDirectly("delete@example.com", "Password1",
                    UserRoleEnum.MARKETING_USER, companyId);

            mockMvc.perform(delete("/api/admin/users/" + user.getUserId())
                            .header("Authorization", "Bearer " + adminToken))
                    .andExpect(status().isNoContent());

            assertThat(userRepository.findById(user.getUserId())).isEmpty();
        }

        @Test
        void shouldReject400_whenAdminDeletesSelf() throws Exception {
            mockMvc.perform(delete("/api/admin/users/" + adminUser.getUserId())
                            .header("Authorization", "Bearer " + adminToken))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("Cannot delete your own account"));
        }

        @Test
        void shouldReject404_whenDeletingNonExistentUser() throws Exception {
            mockMvc.perform(delete("/api/admin/users/99999")
                            .header("Authorization", "Bearer " + adminToken))
                    .andExpect(status().isNotFound());
        }
    }

    // ==================== System Analytics ====================

    @Nested
    class Analytics {

        @Test
        void shouldReturnAnalytics_whenAdmin() throws Exception {
            // Seed data: admin user already exists, create more
            createUserDirectly("user1@example.com", "Password1",
                    UserRoleEnum.MARKETING_USER, companyId);

            Company otherCompany = new Company();
            otherCompany.setCompanyName("Other Corp");
            companyRepository.save(otherCompany);

            Brand brand = Brand.builder()
                    .brandName("Test Brand")
                    .company(companyRepository.findById(companyId).orElseThrow())
                    .build();
            brandRepository.save(brand);

            Report completedReport = Report.builder()
                    .user(adminUser)
                    .brand(brand)
                    .dataSource(DataSourceEnum.REDDIT)
                    .status(ReportStatusEnum.COMPLETED)
                    .build();
            reportRepository.save(completedReport);

            Report pendingReport = Report.builder()
                    .user(adminUser)
                    .brand(brand)
                    .dataSource(DataSourceEnum.CSV_UPLOAD)
                    .status(ReportStatusEnum.PENDING)
                    .build();
            reportRepository.save(pendingReport);

            Post post1 = Post.builder()
                    .report(completedReport)
                    .postText("Great product!")
                    .language(LanguageEnum.EN)
                    .build();
            postRepository.save(post1);

            mockMvc.perform(get("/api/admin/analytics")
                            .header("Authorization", "Bearer " + adminToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalUsers").value(2))
                    .andExpect(jsonPath("$.totalCompanies").value(2))
                    .andExpect(jsonPath("$.totalReports").value(2))
                    .andExpect(jsonPath("$.reportsByStatus.COMPLETED").value(1))
                    .andExpect(jsonPath("$.reportsByStatus.PENDING").value(1))
                    .andExpect(jsonPath("$.reportsByStatus.PROCESSING").value(0))
                    .andExpect(jsonPath("$.reportsByStatus.FAILED").value(0))
                    .andExpect(jsonPath("$.totalPostsAnalyzed").value(1));
        }

        @Test
        void shouldReject403_whenNonAdminAccessesAnalytics() throws Exception {
            User marketingUser = createUserDirectly("marketing@example.com", "Password1",
                    UserRoleEnum.MARKETING_USER, companyId);
            String marketingToken = jwtService.generateAccessToken(marketingUser);

            mockMvc.perform(get("/api/admin/analytics")
                            .header("Authorization", "Bearer " + marketingToken))
                    .andExpect(status().isForbidden());
        }

        @Test
        void shouldReturnZeroCounts_whenNoData() throws Exception {
            // Only admin user and 1 company exist from setUp
            mockMvc.perform(get("/api/admin/analytics")
                            .header("Authorization", "Bearer " + adminToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalUsers").value(1))
                    .andExpect(jsonPath("$.totalCompanies").value(1))
                    .andExpect(jsonPath("$.totalReports").value(0))
                    .andExpect(jsonPath("$.totalPostsAnalyzed").value(0));
        }
    }

    // ==================== Reported Reviews ====================

    @Nested
    class ReportedReviews {

        @Test
        void shouldReturnReportedReviews_withFullContext_whenAdmin() throws Exception {
            // Build the full chain: Company -> Brand -> Report -> Post -> Review -> Feedback
            Company company = companyRepository.findById(companyId).orElseThrow();

            Brand brand = Brand.builder()
                    .brandName("Acme Widget")
                    .company(company)
                    .build();
            brandRepository.save(brand);

            Report report = Report.builder()
                    .user(adminUser)
                    .brand(brand)
                    .dataSource(DataSourceEnum.REDDIT)
                    .status(ReportStatusEnum.COMPLETED)
                    .build();
            reportRepository.save(report);

            Post aPost = Post.builder()
                    .report(report)
                    .postText("This product is amazing!")
                    .language(LanguageEnum.EN)
                    .build();
            postRepository.save(aPost);

            Review review = Review.builder()
                    .post(aPost)
                    .score(new BigDecimal("4.5"))
                    .llmScore(new BigDecimal("4.2"))
                    .emotion(EmotionEnum.JOY)
                    .aspect(AspectEnum.PRODUCT)
                    .build();
            reviewRepository.save(review);

            User reporter = createUserDirectly("reporter@example.com", "Password1",
                    UserRoleEnum.MARKETING_USER, companyId);

            Feedback feedback = Feedback.builder()
                    .review(review)
                    .user(reporter)
                    .brief("Score seems too high for this post")
                    .build();
            feedbackRepository.save(feedback);

            mockMvc.perform(get("/api/admin/reported-reviews")
                            .param("page", "0")
                            .param("size", "10")
                            .header("Authorization", "Bearer " + adminToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalElements").value(1))
                    .andExpect(jsonPath("$.content[0].feedbackId").isNumber())
                    .andExpect(jsonPath("$.content[0].brief").value("Score seems too high for this post"))
                    .andExpect(jsonPath("$.content[0].review.score").value(4.5))
                    .andExpect(jsonPath("$.content[0].review.llmScore").value(4.2))
                    .andExpect(jsonPath("$.content[0].review.emotion").value("JOY"))
                    .andExpect(jsonPath("$.content[0].review.aspect").value("PRODUCT"))
                    .andExpect(jsonPath("$.content[0].post.postId").isNumber())
                    .andExpect(jsonPath("$.content[0].post.postText").value("This product is amazing!"))
                    .andExpect(jsonPath("$.content[0].post.language").value("EN"))
                    .andExpect(jsonPath("$.content[0].post.createdAt").exists())
                    .andExpect(jsonPath("$.content[0].brandName").value("Acme Widget"))
                    .andExpect(jsonPath("$.content[0].companyName").value("Test Corp"))
                    .andExpect(jsonPath("$.content[0].reporter.email").value("reporter@example.com"));
        }

        @Test
        void shouldReturnEmptyPage_whenNoFeedback() throws Exception {
            mockMvc.perform(get("/api/admin/reported-reviews")
                            .header("Authorization", "Bearer " + adminToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalElements").value(0))
                    .andExpect(jsonPath("$.content").isArray())
                    .andExpect(jsonPath("$.content").isEmpty());
        }

        @Test
        void shouldReject403_whenNonAdminAccessesReportedReviews() throws Exception {
            User marketingUser = createUserDirectly("marketing@example.com", "Password1",
                    UserRoleEnum.MARKETING_USER, companyId);
            String marketingToken = jwtService.generateAccessToken(marketingUser);

            mockMvc.perform(get("/api/admin/reported-reviews")
                            .header("Authorization", "Bearer " + marketingToken))
                    .andExpect(status().isForbidden());
        }
    }

    // ==================== Registration Lockdown ====================

    @Nested
    class RegistrationLockdown {

        @Test
        void shouldReject403_whenRegisterEndpointCalledByNonAdmin() throws Exception {
            User marketingUser = createUserDirectly("marketing@example.com", "Password1",
                    UserRoleEnum.MARKETING_USER, companyId);
            String marketingToken = jwtService.generateAccessToken(marketingUser);

            mockMvc.perform(post("/api/auth/register")
                            .header("Authorization", "Bearer " + marketingToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                        "firstName": "New",
                                        "lastName": "User",
                                        "email": "new@example.com",
                                        "password": "Password1",
                                        "companyId": %d,
                                        "role": "MARKETING_USER"
                                    }
                                    """.formatted(companyId)))
                    .andExpect(status().isForbidden());
        }

        @Test
        void shouldRejectRegister_whenUnauthenticated() throws Exception {
            mockMvc.perform(post("/api/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                        "firstName": "New",
                                        "lastName": "User",
                                        "email": "new@example.com",
                                        "password": "Password1",
                                        "companyId": %d,
                                        "role": "MARKETING_USER"
                                    }
                                    """.formatted(companyId)))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        void shouldAllowRegister_whenAdmin() throws Exception {
            mockMvc.perform(post("/api/auth/register")
                            .header("Authorization", "Bearer " + adminToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                        "firstName": "New",
                                        "lastName": "User",
                                        "email": "new@example.com",
                                        "password": "Password1",
                                        "companyId": %d,
                                        "role": "MARKETING_USER"
                                    }
                                    """.formatted(companyId)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.userId").isNumber());
        }
    }

    // ==================== Company Management ====================

    @Nested
    class CompanyManagement {

        @Test
        void shouldCreateCompany_whenAdmin() throws Exception {
            mockMvc.perform(post("/api/admin/companies")
                            .header("Authorization", "Bearer " + adminToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"companyName": "New Corp"}
                                    """))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.companyId").isNumber())
                    .andExpect(jsonPath("$.companyName").value("New Corp"))
                    .andExpect(jsonPath("$.createdAt").isNotEmpty());
        }

        @Test
        void shouldListCompanies_paginated() throws Exception {
            // setUp already created 1 company
            mockMvc.perform(get("/api/admin/companies")
                            .header("Authorization", "Bearer " + adminToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalElements").value(1))
                    .andExpect(jsonPath("$.content[0].companyName").value("Test Corp"));
        }

        @Test
        void shouldGetCompany() throws Exception {
            mockMvc.perform(get("/api/admin/companies/" + companyId)
                            .header("Authorization", "Bearer " + adminToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.companyName").value("Test Corp"));
        }

        @Test
        void shouldUpdateCompany() throws Exception {
            mockMvc.perform(put("/api/admin/companies/" + companyId)
                            .header("Authorization", "Bearer " + adminToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"companyName": "Renamed Corp"}
                                    """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.companyName").value("Renamed Corp"));
        }

        @Test
        void shouldDeleteCompany() throws Exception {
            Company toDelete = new Company();
            toDelete.setCompanyName("Delete Me");
            toDelete = companyRepository.save(toDelete);

            mockMvc.perform(delete("/api/admin/companies/" + toDelete.getCompanyId())
                            .header("Authorization", "Bearer " + adminToken))
                    .andExpect(status().isNoContent());
        }

        @Test
        void shouldReject404_whenGettingNonExistentCompany() throws Exception {
            mockMvc.perform(get("/api/admin/companies/99999")
                            .header("Authorization", "Bearer " + adminToken))
                    .andExpect(status().isNotFound());
        }
    }

    // ==================== Brand Management ====================

    @Nested
    class BrandManagement {

        @Test
        void shouldCreateBrand_whenAdmin() throws Exception {
            mockMvc.perform(post("/api/admin/brands")
                            .header("Authorization", "Bearer " + adminToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"brandName": "Acme", "companyId": %d, "industry": "Tech"}
                                    """.formatted(companyId)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.brandId").isNumber())
                    .andExpect(jsonPath("$.brandName").value("Acme"))
                    .andExpect(jsonPath("$.company.companyId").value(companyId))
                    .andExpect(jsonPath("$.industry").value("Tech"));
        }

        @Test
        void shouldReject404_whenCreatingBrandWithInvalidCompany() throws Exception {
            mockMvc.perform(post("/api/admin/brands")
                            .header("Authorization", "Bearer " + adminToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"brandName": "Acme", "companyId": 99999}
                                    """))
                    .andExpect(status().isNotFound());
        }

        @Test
        void shouldListBrands_paginated() throws Exception {
            Brand brand = Brand.builder()
                    .brandName("Test Brand")
                    .company(companyRepository.findById(companyId).orElseThrow())
                    .build();
            brandRepository.save(brand);

            mockMvc.perform(get("/api/admin/brands")
                            .header("Authorization", "Bearer " + adminToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalElements").value(1))
                    .andExpect(jsonPath("$.content[0].brandName").value("Test Brand"));
        }

        @Test
        void shouldFilterBrandsByCompany() throws Exception {
            Brand brand = Brand.builder()
                    .brandName("Filtered Brand")
                    .company(companyRepository.findById(companyId).orElseThrow())
                    .build();
            brandRepository.save(brand);

            Company other = new Company();
            other.setCompanyName("Other");
            other = companyRepository.save(other);

            Brand otherBrand = Brand.builder()
                    .brandName("Other Brand")
                    .company(other)
                    .build();
            brandRepository.save(otherBrand);

            mockMvc.perform(get("/api/admin/brands")
                            .param("companyId", companyId.toString())
                            .header("Authorization", "Bearer " + adminToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalElements").value(1))
                    .andExpect(jsonPath("$.content[0].brandName").value("Filtered Brand"));
        }

        @Test
        void shouldUpdateBrand() throws Exception {
            Brand brand = Brand.builder()
                    .brandName("Old Name")
                    .company(companyRepository.findById(companyId).orElseThrow())
                    .build();
            brand = brandRepository.save(brand);

            mockMvc.perform(put("/api/admin/brands/" + brand.getBrandId())
                            .header("Authorization", "Bearer " + adminToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"brandName": "New Name"}
                                    """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.brandName").value("New Name"));
        }

        @Test
        void shouldDeleteBrand() throws Exception {
            Brand brand = Brand.builder()
                    .brandName("Delete Me")
                    .company(companyRepository.findById(companyId).orElseThrow())
                    .build();
            brand = brandRepository.save(brand);

            mockMvc.perform(delete("/api/admin/brands/" + brand.getBrandId())
                            .header("Authorization", "Bearer " + adminToken))
                    .andExpect(status().isNoContent());
        }
    }

    // ==================== Helpers ====================

    private User createUserDirectly(String email, String password,
                                     UserRoleEnum role, Long targetCompanyId) {
        Company company = targetCompanyId == null ? null
                : companyRepository.findById(targetCompanyId).orElseThrow();
        User user = User.builder()
                .firstName("Jane")
                .lastName("Doe")
                .email(email)
                .password(passwordEncoder.encode(password))
                .company(company)
                .role(role)
                .build();
        return userRepository.save(user);
    }

    private String createUserJson(String email, String password,
                                   Long targetCompanyId, String role) {
        String companyIdLiteral = targetCompanyId == null ? "null" : targetCompanyId.toString();
        return """
                {
                    "firstName": "Jane",
                    "lastName": "Doe",
                    "email": "%s",
                    "password": "%s",
                    "companyId": %s,
                    "role": "%s"
                }
                """.formatted(email, password, companyIdLiteral, role);
    }
}
