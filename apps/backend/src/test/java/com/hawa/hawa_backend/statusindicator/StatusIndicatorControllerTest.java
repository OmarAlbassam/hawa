package com.hawa.hawa_backend.statusindicator;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;

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
import com.hawa.hawa_backend.report.Report;
import com.hawa.hawa_backend.report.ReportRepository;
import com.hawa.hawa_backend.review.Review;
import com.hawa.hawa_backend.review.ReviewRepository;
import com.hawa.hawa_backend.user.User;
import com.hawa.hawa_backend.user.UserRepository;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class StatusIndicatorControllerTest {

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
    class GetReportStatusIndicator {

        @Test
        void shouldReturnFullStatusIndicator_whenReportCompleted() throws Exception {
            Report report = createReport(brand, ReportStatusEnum.COMPLETED, "Overall positive sentiment with strong product satisfaction.");
            createReview(report, new BigDecimal("4.0"), EmotionEnum.JOY, AspectEnum.PRODUCT);
            createReview(report, new BigDecimal("4.5"), EmotionEnum.JOY, AspectEnum.PRODUCT);
            createReview(report, new BigDecimal("2.0"), EmotionEnum.ANGER, AspectEnum.SERVICE);
            createReview(report, new BigDecimal("2.5"), EmotionEnum.SADNESS, AspectEnum.PRICING);

            mockMvc.perform(get("/api/reports/" + report.getReportId() + "/status-indicator")
                            .header("Authorization", "Bearer " + userToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.averageSentiment").value(65.00))
                    .andExpect(jsonPath("$.sentimentCategory").value("POSITIVE"))
                    .andExpect(jsonPath("$.totalAnalyzedPosts").value(4))
                    .andExpect(jsonPath("$.summary").value("Overall positive sentiment with strong product satisfaction."))
                    .andExpect(jsonPath("$.dominantEmotion").value("JOY"))
                    .andExpect(jsonPath("$.topEmotions[0].emotion").value("JOY"))
                    .andExpect(jsonPath("$.topEmotions[0].count").value(2))
                    .andExpect(jsonPath("$.topEmotions[0].percentage").value(50.00))
                    .andExpect(jsonPath("$.topEmotions.length()").value(3))
                    .andExpect(jsonPath("$.sentimentBreakdown.negative").value(1))
                    .andExpect(jsonPath("$.sentimentBreakdown.neutral").value(1))
                    .andExpect(jsonPath("$.sentimentBreakdown.positive").value(2))
                    .andExpect(jsonPath("$.emotionDiversity").exists());
        }

        @Test
        void shouldClassifyAsNegative_whenAverageAtMost2() throws Exception {
            Report report = createReport(brand, ReportStatusEnum.COMPLETED);
            createReview(report, new BigDecimal("1.5"), EmotionEnum.ANGER, AspectEnum.PRODUCT);
            createReview(report, new BigDecimal("2.0"), EmotionEnum.SADNESS, AspectEnum.SERVICE);

            mockMvc.perform(get("/api/reports/" + report.getReportId() + "/status-indicator")
                            .header("Authorization", "Bearer " + userToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.averageSentiment").value(35.00))
                    .andExpect(jsonPath("$.sentimentCategory").value("NEGATIVE"));
        }

        @Test
        void shouldClassifyAsNeutral_whenAverageBetween2And3Exclusive() throws Exception {
            Report report = createReport(brand, ReportStatusEnum.COMPLETED);
            createReview(report, new BigDecimal("2.5"), EmotionEnum.NEUTRAL, AspectEnum.PRODUCT);
            createReview(report, new BigDecimal("2.5"), EmotionEnum.NEUTRAL, AspectEnum.SERVICE);

            mockMvc.perform(get("/api/reports/" + report.getReportId() + "/status-indicator")
                            .header("Authorization", "Bearer " + userToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.averageSentiment").value(50.00))
                    .andExpect(jsonPath("$.sentimentCategory").value("NEUTRAL"));
        }

        @Test
        void shouldClassifyAsPositive_whenAverageAtLeast3() throws Exception {
            Report report = createReport(brand, ReportStatusEnum.COMPLETED);
            createReview(report, new BigDecimal("3.0"), EmotionEnum.JOY, AspectEnum.PRODUCT);
            createReview(report, new BigDecimal("5.0"), EmotionEnum.JOY, AspectEnum.PRODUCT);

            mockMvc.perform(get("/api/reports/" + report.getReportId() + "/status-indicator")
                            .header("Authorization", "Bearer " + userToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.averageSentiment").value(80.00))
                    .andExpect(jsonPath("$.sentimentCategory").value("POSITIVE"));
        }

        @Test
        void shouldLimitTopEmotionsToThree_sortedByCount() throws Exception {
            Report report = createReport(brand, ReportStatusEnum.COMPLETED);
            // JOY=4, ANGER=3, SADNESS=2, FEAR=1
            for (int i = 0; i < 4; i++) createReview(report, new BigDecimal("4.0"), EmotionEnum.JOY, AspectEnum.PRODUCT);
            for (int i = 0; i < 3; i++) createReview(report, new BigDecimal("2.0"), EmotionEnum.ANGER, AspectEnum.SERVICE);
            for (int i = 0; i < 2; i++) createReview(report, new BigDecimal("2.0"), EmotionEnum.SADNESS, AspectEnum.PRICING);
            createReview(report, new BigDecimal("3.0"), EmotionEnum.FEAR, AspectEnum.PRODUCT);

            mockMvc.perform(get("/api/reports/" + report.getReportId() + "/status-indicator")
                            .header("Authorization", "Bearer " + userToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.topEmotions.length()").value(3))
                    .andExpect(jsonPath("$.topEmotions[0].emotion").value("JOY"))
                    .andExpect(jsonPath("$.topEmotions[0].count").value(4))
                    .andExpect(jsonPath("$.topEmotions[1].emotion").value("ANGER"))
                    .andExpect(jsonPath("$.topEmotions[1].count").value(3))
                    .andExpect(jsonPath("$.topEmotions[2].emotion").value("SADNESS"))
                    .andExpect(jsonPath("$.topEmotions[2].count").value(2))
                    .andExpect(jsonPath("$.dominantEmotion").value("JOY"));
        }

        @Test
        void shouldReturnDiversityZero_whenAllReviewsShareOneEmotion() throws Exception {
            Report report = createReport(brand, ReportStatusEnum.COMPLETED);
            createReview(report, new BigDecimal("4.0"), EmotionEnum.JOY, AspectEnum.PRODUCT);
            createReview(report, new BigDecimal("4.5"), EmotionEnum.JOY, AspectEnum.PRODUCT);

            mockMvc.perform(get("/api/reports/" + report.getReportId() + "/status-indicator")
                            .header("Authorization", "Bearer " + userToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.emotionDiversity").value(0.0));
        }

        @Test
        void shouldReturnDiversityOne_whenEmotionsAreEvenlyDistributed() throws Exception {
            Report report = createReport(brand, ReportStatusEnum.COMPLETED);
            createReview(report, new BigDecimal("4.0"), EmotionEnum.JOY, AspectEnum.PRODUCT);
            createReview(report, new BigDecimal("2.0"), EmotionEnum.ANGER, AspectEnum.SERVICE);
            createReview(report, new BigDecimal("2.0"), EmotionEnum.SADNESS, AspectEnum.PRICING);

            mockMvc.perform(get("/api/reports/" + report.getReportId() + "/status-indicator")
                            .header("Authorization", "Bearer " + userToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.emotionDiversity").value(1.0));
        }

        @Test
        void shouldExcludeIrrelevantPostsFromAggregation() throws Exception {
            Report report = createReport(brand, ReportStatusEnum.COMPLETED);
            createReview(report, new BigDecimal("4.0"), EmotionEnum.JOY, AspectEnum.PRODUCT);
            createReview(report, new BigDecimal("2.0"), EmotionEnum.ANGER, AspectEnum.SERVICE);
            createIrrelevantPost(report, "spam", IrrelevanceReasonEnum.SPAM);
            createIrrelevantPost(report, "off-topic", IrrelevanceReasonEnum.HOMONYM);

            mockMvc.perform(get("/api/reports/" + report.getReportId() + "/status-indicator")
                            .header("Authorization", "Bearer " + userToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalAnalyzedPosts").value(2))
                    .andExpect(jsonPath("$.averageSentiment").value(60.00));
        }

        @Test
        void shouldReturnEmptyShape_whenCompletedReportHasNoReviews() throws Exception {
            Report report = createReport(brand, ReportStatusEnum.COMPLETED);

            mockMvc.perform(get("/api/reports/" + report.getReportId() + "/status-indicator")
                            .header("Authorization", "Bearer " + userToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.averageSentiment").doesNotExist())
                    .andExpect(jsonPath("$.sentimentCategory").doesNotExist())
                    .andExpect(jsonPath("$.dominantEmotion").doesNotExist())
                    .andExpect(jsonPath("$.emotionDiversity").doesNotExist())
                    .andExpect(jsonPath("$.topEmotions.length()").value(0))
                    .andExpect(jsonPath("$.sentimentBreakdown.negative").value(0))
                    .andExpect(jsonPath("$.sentimentBreakdown.neutral").value(0))
                    .andExpect(jsonPath("$.sentimentBreakdown.positive").value(0))
                    .andExpect(jsonPath("$.totalAnalyzedPosts").value(0));
        }

        @Test
        void shouldReturn400_whenReportNotCompleted() throws Exception {
            Report report = createReport(brand, ReportStatusEnum.PROCESSING);

            mockMvc.perform(get("/api/reports/" + report.getReportId() + "/status-indicator")
                            .header("Authorization", "Bearer " + userToken))
                    .andExpect(status().isBadRequest());
        }

        @Test
        void shouldReturn404_whenReportDoesNotExist() throws Exception {
            mockMvc.perform(get("/api/reports/999999/status-indicator")
                            .header("Authorization", "Bearer " + userToken))
                    .andExpect(status().isNotFound());
        }

        @Test
        void shouldReturn404_whenReportBelongsToDifferentCompany() throws Exception {
            Company otherCompany = new Company();
            otherCompany.setCompanyName("Other Corp");
            otherCompany = companyRepository.save(otherCompany);
            Brand otherBrand = brandRepository.save(Brand.builder()
                    .brandName("Adidas").company(otherCompany).build());
            User otherUser = createUser("other@example.com", UserRoleEnum.MARKETING_USER, otherCompany);
            Report report = reportRepository.save(Report.builder()
                    .brand(otherBrand)
                    .user(otherUser)
                    .dataSource(DataSourceEnum.REDDIT)
                    .status(ReportStatusEnum.COMPLETED)
                    .build());

            mockMvc.perform(get("/api/reports/" + report.getReportId() + "/status-indicator")
                            .header("Authorization", "Bearer " + userToken))
                    .andExpect(status().isNotFound());
        }

        @Test
        void shouldReturn401_whenUnauthenticated() throws Exception {
            Report report = createReport(brand, ReportStatusEnum.COMPLETED);

            mockMvc.perform(get("/api/reports/" + report.getReportId() + "/status-indicator"))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    class GetBrandStatusIndicator {

        @Test
        void shouldAggregateAcrossMultipleCompletedReports() throws Exception {
            Report r1 = createReport(brand, ReportStatusEnum.COMPLETED);
            Report r2 = createReport(brand, ReportStatusEnum.COMPLETED);
            createReview(r1, new BigDecimal("4.0"), EmotionEnum.JOY, AspectEnum.PRODUCT);
            createReview(r2, new BigDecimal("2.0"), EmotionEnum.ANGER, AspectEnum.SERVICE);

            mockMvc.perform(get("/api/brands/" + brand.getBrandId() + "/status-indicator")
                            .header("Authorization", "Bearer " + userToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalAnalyzedPosts").value(2))
                    .andExpect(jsonPath("$.averageSentiment").value(60.00))
                    .andExpect(jsonPath("$.sentimentBreakdown.negative").value(1))
                    .andExpect(jsonPath("$.sentimentBreakdown.positive").value(1));
        }

        @Test
        void shouldIgnoreNonCompletedReports() throws Exception {
            Report completed = createReport(brand, ReportStatusEnum.COMPLETED);
            Report pending = createReport(brand, ReportStatusEnum.PENDING);
            Report failed = createReport(brand, ReportStatusEnum.FAILED);
            createReview(completed, new BigDecimal("4.0"), EmotionEnum.JOY, AspectEnum.PRODUCT);
            createReview(pending, new BigDecimal("1.0"), EmotionEnum.ANGER, AspectEnum.SERVICE);
            createReview(failed, new BigDecimal("1.0"), EmotionEnum.ANGER, AspectEnum.SERVICE);

            mockMvc.perform(get("/api/brands/" + brand.getBrandId() + "/status-indicator")
                            .header("Authorization", "Bearer " + userToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalAnalyzedPosts").value(1))
                    .andExpect(jsonPath("$.averageSentiment").value(80.00));
        }

        @Test
        void shouldReturnSummaryFromMostRecentCompletedReport() throws Exception {
            Report older = createReport(brand, ReportStatusEnum.COMPLETED, "Older summary");
            Report newer = createReport(brand, ReportStatusEnum.COMPLETED, "Latest brand summary");
            setReportFinishedAt(older.getReportId(), "2026-01-01 12:00:00");
            setReportFinishedAt(newer.getReportId(), "2026-04-01 12:00:00");
            createReview(older, new BigDecimal("4.0"), EmotionEnum.JOY, AspectEnum.PRODUCT);
            createReview(newer, new BigDecimal("3.0"), EmotionEnum.NEUTRAL, AspectEnum.PRODUCT);

            mockMvc.perform(get("/api/brands/" + brand.getBrandId() + "/status-indicator")
                            .header("Authorization", "Bearer " + userToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.summary").value("Latest brand summary"));
        }

        @Test
        void shouldReturnEmptyShape_whenBrandHasNoCompletedReports() throws Exception {
            createReport(brand, ReportStatusEnum.PENDING);

            mockMvc.perform(get("/api/brands/" + brand.getBrandId() + "/status-indicator")
                            .header("Authorization", "Bearer " + userToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalAnalyzedPosts").value(0))
                    .andExpect(jsonPath("$.averageSentiment").doesNotExist())
                    .andExpect(jsonPath("$.sentimentCategory").doesNotExist())
                    .andExpect(jsonPath("$.summary").doesNotExist())
                    .andExpect(jsonPath("$.topEmotions.length()").value(0));
        }

        @Test
        void shouldReturn404_whenBrandDoesNotExist() throws Exception {
            mockMvc.perform(get("/api/brands/999999/status-indicator")
                            .header("Authorization", "Bearer " + userToken))
                    .andExpect(status().isNotFound());
        }

        @Test
        void shouldReturn404_whenBrandBelongsToDifferentCompany() throws Exception {
            Company otherCompany = new Company();
            otherCompany.setCompanyName("Other Corp");
            otherCompany = companyRepository.save(otherCompany);
            Brand otherBrand = brandRepository.save(Brand.builder()
                    .brandName("Adidas").company(otherCompany).build());

            mockMvc.perform(get("/api/brands/" + otherBrand.getBrandId() + "/status-indicator")
                            .header("Authorization", "Bearer " + userToken))
                    .andExpect(status().isNotFound());
        }

        @Test
        void shouldReturn401_whenUnauthenticated() throws Exception {
            mockMvc.perform(get("/api/brands/" + brand.getBrandId() + "/status-indicator"))
                    .andExpect(status().isUnauthorized());
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
        return createReport(targetBrand, status, null);
    }

    private Report createReport(Brand targetBrand, ReportStatusEnum status, String summary) {
        Report report = Report.builder()
                .brand(targetBrand)
                .user(marketingUser)
                .dataSource(DataSourceEnum.REDDIT)
                .status(status)
                .summary(summary)
                .finishedAt(status == ReportStatusEnum.COMPLETED ? LocalDateTime.now() : null)
                .build();
        return reportRepository.save(report);
    }

    private Review createReview(Report report, BigDecimal score,
                                EmotionEnum emotion, AspectEnum aspect) {
        Post post = Post.builder()
                .report(report)
                .postText("sample text")
                .language(LanguageEnum.EN)
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

    private void setReportFinishedAt(Long reportId, String timestamp) {
        jdbcTemplate.update(
                "UPDATE report SET finished_at = ? WHERE report_id = ?",
                Timestamp.valueOf(timestamp), reportId);
    }
}
