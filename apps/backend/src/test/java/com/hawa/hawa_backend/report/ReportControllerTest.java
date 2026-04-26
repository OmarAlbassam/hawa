package com.hawa.hawa_backend.report;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hawa.hawa_backend.TestcontainersConfiguration;
import com.hawa.hawa_backend.auth.JwtService;
import com.hawa.hawa_backend.brand.Brand;
import com.hawa.hawa_backend.brand.BrandRepository;
import com.hawa.hawa_backend.company.Company;
import com.hawa.hawa_backend.company.CompanyRepository;
import com.hawa.hawa_backend.enums.AspectEnum;
import com.hawa.hawa_backend.enums.DataSourceEnum;
import com.hawa.hawa_backend.enums.EmotionEnum;
import com.hawa.hawa_backend.enums.IrrelevanceReasonEnum;
import com.hawa.hawa_backend.enums.LanguageEnum;
import com.hawa.hawa_backend.enums.RelevanceStatusEnum;
import com.hawa.hawa_backend.enums.ReportStatusEnum;
import com.hawa.hawa_backend.enums.UserRoleEnum;
import com.hawa.hawa_backend.post.Post;
import com.hawa.hawa_backend.post.PostRepository;
import com.hawa.hawa_backend.review.Review;
import com.hawa.hawa_backend.review.ReviewRepository;
import com.hawa.hawa_backend.user.User;
import com.hawa.hawa_backend.user.UserRepository;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class ReportControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private CompanyRepository companyRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private BrandRepository brandRepository;
    @Autowired private ReportRepository reportRepository;
    @Autowired private PostRepository postRepository;
    @Autowired private ReviewRepository reviewRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JwtService jwtService;
    @Autowired private JdbcTemplate jdbcTemplate;

    private Company company;
    private User marketingUser;
    private String userToken;
    private Brand brand;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("DELETE FROM feedback");
        jdbcTemplate.execute("DELETE FROM review");
        jdbcTemplate.execute("DELETE FROM post");
        jdbcTemplate.execute("DELETE FROM report");
        jdbcTemplate.execute("DELETE FROM keyword");
        jdbcTemplate.execute("DELETE FROM brand");
        jdbcTemplate.execute("DELETE FROM refresh_token");
        jdbcTemplate.execute("DELETE FROM \"user\"");
        jdbcTemplate.execute("DELETE FROM company");

        company = new Company();
        company.setCompanyName("Test Corp");
        company = companyRepository.save(company);

        marketingUser = createUser("marketing@example.com", UserRoleEnum.MARKETING_USER, company);
        userToken = jwtService.generateAccessToken(marketingUser);

        brand = Brand.builder()
                .brandName("Nike")
                .company(company)
                .build();
        brand = brandRepository.save(brand);
    }

    @Nested
    class ListReports {

        @Test
        void shouldReturnPaginatedReports() throws Exception {
            createReport(brand, ReportStatusEnum.COMPLETED);
            createReport(brand, ReportStatusEnum.PENDING);

            mockMvc.perform(get("/api/reports")
                            .header("Authorization", "Bearer " + userToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content").isArray())
                    .andExpect(jsonPath("$.content.length()").value(2))
                    .andExpect(jsonPath("$.content[0].reportId").isNumber())
                    .andExpect(jsonPath("$.content[0].brandName").value("Nike"))
                    .andExpect(jsonPath("$.totalElements").value(2));
        }

        @Test
        void shouldFilterByBrandId() throws Exception {
            Brand otherBrand = Brand.builder()
                    .brandName("Adidas")
                    .company(company)
                    .build();
            otherBrand = brandRepository.save(otherBrand);

            createReport(brand, ReportStatusEnum.COMPLETED);
            createReport(otherBrand, ReportStatusEnum.COMPLETED);

            mockMvc.perform(get("/api/reports")
                            .param("brandId", brand.getBrandId().toString())
                            .header("Authorization", "Bearer " + userToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content.length()").value(1))
                    .andExpect(jsonPath("$.content[0].brandName").value("Nike"));
        }

        @Test
        void shouldFilterByStatus() throws Exception {
            createReport(brand, ReportStatusEnum.COMPLETED);
            createReport(brand, ReportStatusEnum.PENDING);
            createReport(brand, ReportStatusEnum.FAILED);

            mockMvc.perform(get("/api/reports")
                            .param("status", "COMPLETED")
                            .header("Authorization", "Bearer " + userToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content.length()").value(1))
                    .andExpect(jsonPath("$.content[0].status").value("COMPLETED"));
        }

        @Test
        void shouldNotShowOtherCompanyReports() throws Exception {
            Company otherCompany = new Company();
            otherCompany.setCompanyName("Other Corp");
            otherCompany = companyRepository.save(otherCompany);

            Brand otherBrand = Brand.builder()
                    .brandName("Adidas")
                    .company(otherCompany)
                    .build();
            otherBrand = brandRepository.save(otherBrand);

            User otherUser = createUser("other@example.com", UserRoleEnum.MARKETING_USER, otherCompany);
            Report report = Report.builder()
                    .brand(otherBrand)
                    .user(otherUser)
                    .dataSource(DataSourceEnum.REDDIT)
                    .status(ReportStatusEnum.COMPLETED)
                    .build();
            reportRepository.save(report);

            mockMvc.perform(get("/api/reports")
                            .header("Authorization", "Bearer " + userToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content").isEmpty());
        }

        @Test
        void shouldSortByCreatedAtDesc() throws Exception {
            createReport(brand, ReportStatusEnum.COMPLETED);
            createReport(brand, ReportStatusEnum.PENDING);

            mockMvc.perform(get("/api/reports")
                            .param("sort", "createdAt,desc")
                            .header("Authorization", "Bearer " + userToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content.length()").value(2));
        }

        @Test
        void shouldIncludeReportsCreatedDuringDateToDay() throws Exception {
            // Regression: dateTo filter should include reports created any time on that day
            createReport(brand, ReportStatusEnum.COMPLETED);

            String today = java.time.LocalDate.now().toString();

            mockMvc.perform(get("/api/reports")
                            .param("dateTo", today)
                            .header("Authorization", "Bearer " + userToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content.length()").value(1));
        }

        @Test
        void shouldReject400_whenSortPropertyIsUnknown() throws Exception {
            mockMvc.perform(get("/api/reports")
                            .param("sort", "malicious_col,desc")
                            .header("Authorization", "Bearer " + userToken))
                    .andExpect(status().isBadRequest());
        }

        @Test
        void shouldReturnEmptyPage_whenNoReports() throws Exception {
            mockMvc.perform(get("/api/reports")
                            .header("Authorization", "Bearer " + userToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content").isEmpty())
                    .andExpect(jsonPath("$.totalElements").value(0));
        }
    }

    @Nested
    class GetReportOverview {

        @Test
        void shouldReturnOverview_whenReportCompleted() throws Exception {
            Report report = createReport(brand, ReportStatusEnum.COMPLETED);
            createReview(report, new BigDecimal("4.0"), EmotionEnum.JOY, AspectEnum.PRODUCT);
            createReview(report, new BigDecimal("2.0"), EmotionEnum.ANGER, AspectEnum.SERVICE);
            createReview(report, new BigDecimal("3.0"), EmotionEnum.JOY, AspectEnum.PRODUCT);

            mockMvc.perform(get("/api/reports/" + report.getReportId())
                            .header("Authorization", "Bearer " + userToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.reportId").value(report.getReportId()))
                    .andExpect(jsonPath("$.brandName").value("Nike"))
                    .andExpect(jsonPath("$.status").value("COMPLETED"))
                    .andExpect(jsonPath("$.analyzedPosts").value(3))
                    .andExpect(jsonPath("$.averageSentiment").value(3.0))
                    .andExpect(jsonPath("$.emotionDistribution.JOY").value(2))
                    .andExpect(jsonPath("$.emotionDistribution.ANGER").value(1))
                    .andExpect(jsonPath("$.emotionDistribution.SADNESS").value(0))
                    .andExpect(jsonPath("$.aspectDistribution.PRODUCT").value(2))
                    .andExpect(jsonPath("$.aspectDistribution.SERVICE").value(1))
                    .andExpect(jsonPath("$.aspectDistribution.DELIVERY").value(0))
                    .andExpect(jsonPath("$.aspectDistribution.PRICING").value(0));
        }

        @Test
        void shouldReportFilteredOutCount_andExcludeIrrelevantFromAggregations() throws Exception {
            Report report = createReport(brand, ReportStatusEnum.COMPLETED);
            // Two relevant reviews
            createReview(report, new BigDecimal("4.0"), EmotionEnum.JOY, AspectEnum.PRODUCT);
            createReview(report, new BigDecimal("2.0"), EmotionEnum.ANGER, AspectEnum.SERVICE);
            // Three irrelevant posts — no Review, just Post rows
            createIrrelevantPost(report, "off-topic 1", IrrelevanceReasonEnum.HOMONYM);
            createIrrelevantPost(report, "spam link", IrrelevanceReasonEnum.SPAM);
            createIrrelevantPost(report, "", IrrelevanceReasonEnum.EMPTY);

            mockMvc.perform(get("/api/reports/" + report.getReportId())
                            .header("Authorization", "Bearer " + userToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.analyzedPosts").value(2))
                    .andExpect(jsonPath("$.filteredOutCount").value(3))
                    .andExpect(jsonPath("$.averageSentiment").value(3.0))
                    .andExpect(jsonPath("$.emotionDistribution.JOY").value(1))
                    .andExpect(jsonPath("$.emotionDistribution.ANGER").value(1))
                    .andExpect(jsonPath("$.aspectDistribution.PRODUCT").value(1))
                    .andExpect(jsonPath("$.aspectDistribution.SERVICE").value(1));
        }

        @Test
        void shouldReturnEmptyDistributions_whenCompletedReportHasNoReviews() throws Exception {
            Report report = createReport(brand, ReportStatusEnum.COMPLETED);

            mockMvc.perform(get("/api/reports/" + report.getReportId())
                            .header("Authorization", "Bearer " + userToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.analyzedPosts").value(0))
                    .andExpect(jsonPath("$.averageSentiment").doesNotExist())
                    .andExpect(jsonPath("$.emotionDistribution.JOY").value(0))
                    .andExpect(jsonPath("$.aspectDistribution.PRODUCT").value(0));
        }

        @Test
        void shouldReturn400_whenReportStatusIsProcessing() throws Exception {
            Report report = createReport(brand, ReportStatusEnum.PROCESSING);

            mockMvc.perform(get("/api/reports/" + report.getReportId())
                            .header("Authorization", "Bearer " + userToken))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("PROCESSING")));
        }

        @Test
        void shouldReturn400_whenReportStatusIsFailed() throws Exception {
            Report report = createReport(brand, ReportStatusEnum.FAILED);

            mockMvc.perform(get("/api/reports/" + report.getReportId())
                            .header("Authorization", "Bearer " + userToken))
                    .andExpect(status().isBadRequest());
        }

        @Test
        void shouldReturn404_whenReportDoesNotExist() throws Exception {
            mockMvc.perform(get("/api/reports/999999")
                            .header("Authorization", "Bearer " + userToken))
                    .andExpect(status().isNotFound());
        }

        @Test
        void shouldReturn404_whenReportBelongsToDifferentCompany() throws Exception {
            Company otherCompany = new Company();
            otherCompany.setCompanyName("Other Corp");
            otherCompany = companyRepository.save(otherCompany);

            Brand otherBrand = Brand.builder()
                    .brandName("Adidas")
                    .company(otherCompany)
                    .build();
            otherBrand = brandRepository.save(otherBrand);

            User otherUser = createUser("other@example.com", UserRoleEnum.MARKETING_USER, otherCompany);
            Report report = Report.builder()
                    .brand(otherBrand)
                    .user(otherUser)
                    .dataSource(DataSourceEnum.REDDIT)
                    .status(ReportStatusEnum.COMPLETED)
                    .build();
            report = reportRepository.save(report);

            mockMvc.perform(get("/api/reports/" + report.getReportId())
                            .header("Authorization", "Bearer " + userToken))
                    .andExpect(status().isNotFound());
        }

        @Test
        void shouldReturn401_whenUnauthenticated() throws Exception {
            Report report = createReport(brand, ReportStatusEnum.COMPLETED);

            mockMvc.perform(get("/api/reports/" + report.getReportId()))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    class GetAspectBreakdown {

        @Test
        void shouldReturnRankedAspects_whenReportCompleted() throws Exception {
            Report report = createReport(brand, ReportStatusEnum.COMPLETED);
            // PRODUCT: 3 reviews (2 JOY, 1 SADNESS), avg 3.0
            createReview(report, new BigDecimal("4.0"), EmotionEnum.JOY, AspectEnum.PRODUCT);
            createReview(report, new BigDecimal("3.0"), EmotionEnum.JOY, AspectEnum.PRODUCT);
            createReview(report, new BigDecimal("2.0"), EmotionEnum.SADNESS, AspectEnum.PRODUCT);
            // SERVICE: 1 ANGER, avg 1.0
            createReview(report, new BigDecimal("1.0"), EmotionEnum.ANGER, AspectEnum.SERVICE);

            mockMvc.perform(get("/api/reports/" + report.getReportId() + "/aspects")
                            .header("Authorization", "Bearer " + userToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.reportId").value(report.getReportId()))
                    .andExpect(jsonPath("$.aspects.length()").value(4))
                    .andExpect(jsonPath("$.aspects[0].aspect").value("PRODUCT"))
                    .andExpect(jsonPath("$.aspects[0].postCount").value(3))
                    .andExpect(jsonPath("$.aspects[0].averageSentiment").value(3.0))
                    .andExpect(jsonPath("$.aspects[0].emotionDistribution.JOY").value(2))
                    .andExpect(jsonPath("$.aspects[0].emotionDistribution.SADNESS").value(1))
                    .andExpect(jsonPath("$.aspects[0].emotionDistribution.ANGER").value(0))
                    .andExpect(jsonPath("$.aspects[1].aspect").value("SERVICE"))
                    .andExpect(jsonPath("$.aspects[1].postCount").value(1))
                    .andExpect(jsonPath("$.aspects[1].averageSentiment").value(1.0))
                    .andExpect(jsonPath("$.aspects[1].emotionDistribution.ANGER").value(1));
        }

        @Test
        void shouldReturnAllAspectsWithZeros_whenCompletedReportHasNoReviews() throws Exception {
            Report report = createReport(brand, ReportStatusEnum.COMPLETED);

            mockMvc.perform(get("/api/reports/" + report.getReportId() + "/aspects")
                            .header("Authorization", "Bearer " + userToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.aspects.length()").value(4))
                    .andExpect(jsonPath("$.aspects[0].postCount").value(0))
                    .andExpect(jsonPath("$.aspects[0].averageSentiment").doesNotExist())
                    .andExpect(jsonPath("$.aspects[0].emotionDistribution.JOY").value(0));
        }

        @Test
        void shouldReturnSingleAspect_whenAspectFilterProvided() throws Exception {
            Report report = createReport(brand, ReportStatusEnum.COMPLETED);
            createReview(report, new BigDecimal("4.0"), EmotionEnum.JOY, AspectEnum.PRODUCT);
            createReview(report, new BigDecimal("2.0"), EmotionEnum.ANGER, AspectEnum.SERVICE);

            mockMvc.perform(get("/api/reports/" + report.getReportId() + "/aspects")
                            .param("aspect", "PRODUCT")
                            .header("Authorization", "Bearer " + userToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.aspects.length()").value(1))
                    .andExpect(jsonPath("$.aspects[0].aspect").value("PRODUCT"))
                    .andExpect(jsonPath("$.aspects[0].postCount").value(1));
        }

        @Test
        void shouldReturn400_whenReportNotCompleted() throws Exception {
            Report report = createReport(brand, ReportStatusEnum.PROCESSING);

            mockMvc.perform(get("/api/reports/" + report.getReportId() + "/aspects")
                            .header("Authorization", "Bearer " + userToken))
                    .andExpect(status().isBadRequest());
        }

        @Test
        void shouldReturn404_whenReportDoesNotExist() throws Exception {
            mockMvc.perform(get("/api/reports/999999/aspects")
                            .header("Authorization", "Bearer " + userToken))
                    .andExpect(status().isNotFound());
        }

        @Test
        void shouldReturn404_whenReportBelongsToDifferentCompany() throws Exception {
            Company otherCompany = new Company();
            otherCompany.setCompanyName("Other Corp");
            otherCompany = companyRepository.save(otherCompany);
            Brand otherBrand = Brand.builder().brandName("Adidas").company(otherCompany).build();
            otherBrand = brandRepository.save(otherBrand);
            User otherUser = createUser("other@example.com", UserRoleEnum.MARKETING_USER, otherCompany);
            Report report = Report.builder()
                    .brand(otherBrand)
                    .user(otherUser)
                    .dataSource(DataSourceEnum.REDDIT)
                    .status(ReportStatusEnum.COMPLETED)
                    .build();
            report = reportRepository.save(report);

            mockMvc.perform(get("/api/reports/" + report.getReportId() + "/aspects")
                            .header("Authorization", "Bearer " + userToken))
                    .andExpect(status().isNotFound());
        }

        @Test
        void shouldReturn401_whenUnauthenticated() throws Exception {
            Report report = createReport(brand, ReportStatusEnum.COMPLETED);

            mockMvc.perform(get("/api/reports/" + report.getReportId() + "/aspects"))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    class ListPosts {

        @Test
        void shouldReturnRelevantPostsByDefault_withReviewFields() throws Exception {
            Report report = createReport(brand, ReportStatusEnum.COMPLETED);
            createReview(report, new BigDecimal("4.0"), EmotionEnum.JOY, AspectEnum.PRODUCT);
            createReview(report, new BigDecimal("2.0"), EmotionEnum.ANGER, AspectEnum.SERVICE);
            createIrrelevantPost(report, "off-topic", IrrelevanceReasonEnum.HOMONYM);

            mockMvc.perform(get("/api/reports/" + report.getReportId() + "/posts")
                            .header("Authorization", "Bearer " + userToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content.length()").value(2))
                    .andExpect(jsonPath("$.content[0].score").exists())
                    .andExpect(jsonPath("$.content[0].emotion").exists())
                    .andExpect(jsonPath("$.content[0].aspect").exists())
                    .andExpect(jsonPath("$.content[0].relevanceStatus").value("RELEVANT"));
        }

        @Test
        void shouldReturnIrrelevantPosts_whenRelevanceIrrelevant() throws Exception {
            Report report = createReport(brand, ReportStatusEnum.COMPLETED);
            createReview(report, new BigDecimal("4.0"), EmotionEnum.JOY, AspectEnum.PRODUCT);
            createIrrelevantPost(report, "off-topic chatter", IrrelevanceReasonEnum.HOMONYM);
            createIrrelevantPost(report, "spam!", IrrelevanceReasonEnum.SPAM);

            mockMvc.perform(get("/api/reports/" + report.getReportId() + "/posts")
                            .param("relevance", "IRRELEVANT")
                            .header("Authorization", "Bearer " + userToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content.length()").value(2))
                    .andExpect(jsonPath("$.content[0].relevanceStatus").value("IRRELEVANT"))
                    .andExpect(jsonPath("$.content[0].irrelevanceReason").exists())
                    .andExpect(jsonPath("$.content[0].score").doesNotExist())
                    .andExpect(jsonPath("$.content[0].emotion").doesNotExist())
                    .andExpect(jsonPath("$.content[0].aspect").doesNotExist());
        }

        @Test
        void shouldFilterByEmotion() throws Exception {
            Report report = createReport(brand, ReportStatusEnum.COMPLETED);
            createReview(report, new BigDecimal("4.0"), EmotionEnum.JOY, AspectEnum.PRODUCT);
            createReview(report, new BigDecimal("2.0"), EmotionEnum.ANGER, AspectEnum.SERVICE);

            mockMvc.perform(get("/api/reports/" + report.getReportId() + "/posts")
                            .param("emotion", "JOY")
                            .header("Authorization", "Bearer " + userToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content.length()").value(1))
                    .andExpect(jsonPath("$.content[0].emotion").value("JOY"));
        }

        @Test
        void shouldFilterBySentimentRange() throws Exception {
            Report report = createReport(brand, ReportStatusEnum.COMPLETED);
            createReview(report, new BigDecimal("4.5"), EmotionEnum.JOY, AspectEnum.PRODUCT);
            createReview(report, new BigDecimal("3.0"), EmotionEnum.NEUTRAL, AspectEnum.PRODUCT);
            createReview(report, new BigDecimal("1.5"), EmotionEnum.ANGER, AspectEnum.SERVICE);

            mockMvc.perform(get("/api/reports/" + report.getReportId() + "/posts")
                            .param("sentimentMin", "2.0")
                            .param("sentimentMax", "4.0")
                            .header("Authorization", "Bearer " + userToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content.length()").value(1))
                    .andExpect(jsonPath("$.content[0].score").value(3.0));
        }

        @Test
        void shouldFilterByLanguage() throws Exception {
            Report report = createReport(brand, ReportStatusEnum.COMPLETED);
            createReview(report, new BigDecimal("4.0"),
                    EmotionEnum.JOY, AspectEnum.PRODUCT, LanguageEnum.EN);
            createReview(report, new BigDecimal("3.0"),
                    EmotionEnum.JOY, AspectEnum.PRODUCT, LanguageEnum.AR);

            mockMvc.perform(get("/api/reports/" + report.getReportId() + "/posts")
                            .param("language", "AR")
                            .header("Authorization", "Bearer " + userToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content.length()").value(1))
                    .andExpect(jsonPath("$.content[0].language").value("AR"));
        }

        @Test
        void shouldFilterByDateRange() throws Exception {
            Report report = createReport(brand, ReportStatusEnum.COMPLETED);
            Review older = createReview(report, new BigDecimal("4.0"),
                    EmotionEnum.JOY, AspectEnum.PRODUCT);
            Review middle = createReview(report, new BigDecimal("3.0"),
                    EmotionEnum.NEUTRAL, AspectEnum.PRODUCT);
            Review newer = createReview(report, new BigDecimal("2.0"),
                    EmotionEnum.ANGER, AspectEnum.SERVICE);

            setPostCreatedAt(older.getPost().getPostId(), "2026-01-01 12:00:00");
            setPostCreatedAt(middle.getPost().getPostId(), "2026-03-15 12:00:00");
            setPostCreatedAt(newer.getPost().getPostId(), "2026-06-10 12:00:00");

            mockMvc.perform(get("/api/reports/" + report.getReportId() + "/posts")
                            .param("dateFrom", "2026-03-01")
                            .param("dateTo", "2026-03-31")
                            .header("Authorization", "Bearer " + userToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content.length()").value(1))
                    .andExpect(jsonPath("$.content[0].score").value(3.0));
        }

        @Test
        void shouldFilterIrrelevantByDateRange() throws Exception {
            Report report = createReport(brand, ReportStatusEnum.COMPLETED);
            Post older = createIrrelevantPost(report, "old spam", IrrelevanceReasonEnum.SPAM);
            Post newer = createIrrelevantPost(report, "new spam", IrrelevanceReasonEnum.SPAM);

            setPostCreatedAt(older.getPostId(), "2026-01-01 12:00:00");
            setPostCreatedAt(newer.getPostId(), "2026-06-10 12:00:00");

            mockMvc.perform(get("/api/reports/" + report.getReportId() + "/posts")
                            .param("relevance", "IRRELEVANT")
                            .param("dateFrom", "2026-05-01")
                            .header("Authorization", "Bearer " + userToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content.length()").value(1))
                    .andExpect(jsonPath("$.content[0].postText").value("new spam"));
        }

        @Test
        void shouldSortIrrelevantByCreatedAt() throws Exception {
            Report report = createReport(brand, ReportStatusEnum.COMPLETED);
            Post older = createIrrelevantPost(report, "old spam", IrrelevanceReasonEnum.SPAM);
            Post newer = createIrrelevantPost(report, "new spam", IrrelevanceReasonEnum.SPAM);

            setPostCreatedAt(older.getPostId(), "2026-01-01 12:00:00");
            setPostCreatedAt(newer.getPostId(), "2026-06-10 12:00:00");

            mockMvc.perform(get("/api/reports/" + report.getReportId() + "/posts")
                            .param("relevance", "IRRELEVANT")
                            .param("sort", "createdAt,desc")
                            .header("Authorization", "Bearer " + userToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[0].postText").value("new spam"))
                    .andExpect(jsonPath("$.content[1].postText").value("old spam"));
        }

        @Test
        void shouldIncludeCreatedAt_onResponse() throws Exception {
            Report report = createReport(brand, ReportStatusEnum.COMPLETED);
            createReview(report, new BigDecimal("4.0"),
                    EmotionEnum.JOY, AspectEnum.PRODUCT);

            mockMvc.perform(get("/api/reports/" + report.getReportId() + "/posts")
                            .header("Authorization", "Bearer " + userToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[0].createdAt").exists());
        }

        @Test
        void shouldSortByCreatedAtDesc() throws Exception {
            Report report = createReport(brand, ReportStatusEnum.COMPLETED);
            Review older = createReview(report, new BigDecimal("4.0"),
                    EmotionEnum.JOY, AspectEnum.PRODUCT);
            Review newer = createReview(report, new BigDecimal("2.0"),
                    EmotionEnum.ANGER, AspectEnum.SERVICE);

            setPostCreatedAt(older.getPost().getPostId(), "2026-01-01 12:00:00");
            setPostCreatedAt(newer.getPost().getPostId(), "2026-06-10 12:00:00");

            mockMvc.perform(get("/api/reports/" + report.getReportId() + "/posts")
                            .param("sort", "createdAt,desc")
                            .header("Authorization", "Bearer " + userToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[0].score").value(2.0))
                    .andExpect(jsonPath("$.content[1].score").value(4.0));
        }

        @Test
        void shouldSortByScoreAsc() throws Exception {
            Report report = createReport(brand, ReportStatusEnum.COMPLETED);
            createReview(report, new BigDecimal("4.0"),
                    EmotionEnum.JOY, AspectEnum.PRODUCT);
            createReview(report, new BigDecimal("1.0"),
                    EmotionEnum.ANGER, AspectEnum.SERVICE);

            mockMvc.perform(get("/api/reports/" + report.getReportId() + "/posts")
                            .param("sort", "score,asc")
                            .header("Authorization", "Bearer " + userToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[0].score").value(1.0))
                    .andExpect(jsonPath("$.content[1].score").value(4.0));
        }

        @Test
        void shouldReject400_whenSortByScoreWithIrrelevantRelevance() throws Exception {
            Report report = createReport(brand, ReportStatusEnum.COMPLETED);
            createIrrelevantPost(report, "spam", IrrelevanceReasonEnum.SPAM);

            mockMvc.perform(get("/api/reports/" + report.getReportId() + "/posts")
                            .param("relevance", "IRRELEVANT")
                            .param("sort", "score,desc")
                            .header("Authorization", "Bearer " + userToken))
                    .andExpect(status().isBadRequest());
        }

        @Test
        void shouldReturn404_whenReportBelongsToDifferentCompany() throws Exception {
            Company otherCompany = new Company();
            otherCompany.setCompanyName("Other Corp");
            otherCompany = companyRepository.save(otherCompany);
            Brand otherBrand = Brand.builder().brandName("Adidas").company(otherCompany).build();
            otherBrand = brandRepository.save(otherBrand);
            User otherUser = createUser("other@example.com", UserRoleEnum.MARKETING_USER, otherCompany);
            Report report = Report.builder()
                    .brand(otherBrand)
                    .user(otherUser)
                    .dataSource(DataSourceEnum.REDDIT)
                    .status(ReportStatusEnum.COMPLETED)
                    .build();
            report = reportRepository.save(report);

            mockMvc.perform(get("/api/reports/" + report.getReportId() + "/posts")
                            .header("Authorization", "Bearer " + userToken))
                    .andExpect(status().isNotFound());
        }
    }

    // ==================== Helpers ====================

    private User createUser(String email, UserRoleEnum role, Company targetCompany) {
        User user = User.builder()
                .firstName("Test")
                .lastName("User")
                .email(email)
                .password(passwordEncoder.encode("Password1"))
                .company(targetCompany)
                .role(role)
                .build();
        return userRepository.save(user);
    }

    private Report createReport(Brand targetBrand, ReportStatusEnum status) {
        Report report = Report.builder()
                .brand(targetBrand)
                .user(marketingUser)
                .dataSource(DataSourceEnum.REDDIT)
                .status(status)
                .build();
        return reportRepository.save(report);
    }

    private Review createReview(Report report, BigDecimal score,
                                EmotionEnum emotion, AspectEnum aspect) {
        return createReview(report, score, emotion, aspect, LanguageEnum.EN);
    }

    private Review createReview(Report report, BigDecimal score,
                                EmotionEnum emotion, AspectEnum aspect, LanguageEnum language) {
        Post post = Post.builder()
                .report(report)
                .postText("sample text")
                .language(language)
                .build();
        post = postRepository.save(post);

        Review review = Review.builder()
                .post(post)
                .score(score)
                .emotion(emotion)
                .aspect(aspect)
                .build();
        return reviewRepository.save(review);
    }

    private void setPostCreatedAt(Long postId, String timestamp) {
        jdbcTemplate.update(
                "UPDATE post SET created_at = ? WHERE post_id = ?",
                Timestamp.valueOf(timestamp), postId);
    }

    private Post createIrrelevantPost(Report report, String text, IrrelevanceReasonEnum reason) {
        Post post = Post.builder()
                .report(report)
                .postText(text)
                .language(LanguageEnum.EN)
                .relevanceStatus(RelevanceStatusEnum.IRRELEVANT)
                .irrelevanceReason(reason)
                .build();
        return postRepository.save(post);
    }
}
