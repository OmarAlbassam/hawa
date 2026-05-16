package com.hawa.hawa_backend.features;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.sql.Timestamp;

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

import org.junit.jupiter.api.BeforeEach;
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
class ExportReportCsvTest {

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

    @Test
    void shouldStreamCsvWithRelevantAndIrrelevantRows_whenReportCompleted() throws Exception {
        Report report = createReport(brand, ReportStatusEnum.COMPLETED);
        createReview(report, "Great product!", new BigDecimal("4.0"),
                EmotionEnum.JOY, AspectEnum.PRODUCT, LanguageEnum.EN);
        createReview(report, "Terrible service", new BigDecimal("1.5"),
                EmotionEnum.ANGER, AspectEnum.SERVICE, LanguageEnum.EN);
        createIrrelevantPost(report, "spam link", IrrelevanceReasonEnum.SPAM);

        String body = mockMvc.perform(get("/api/reports/" + report.getReportId() + "/export")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type",
                        org.hamcrest.Matchers.startsWith("text/csv")))
                .andExpect(header().string("Content-Disposition",
                        org.hamcrest.Matchers.containsString(
                                "attachment; filename=\"report-" + report.getReportId() + ".csv\"")))
                .andReturn().getResponse().getContentAsString();

        String[] lines = body.split("\\r?\\n");
        assertThat(lines[0]).isEqualTo("post_text,sentiment_score,emotion,aspect,language,created_at");
        assertThat(lines).hasSize(4);
        assertThat(body).contains("Great product!,4.0,JOY,PRODUCT,EN,");
        assertThat(body).contains("Terrible service,1.5,ANGER,SERVICE,EN,");
        assertThat(body).containsPattern("spam link,,,,EN,[^\\n]+");
    }

    @Test
    void shouldEscapeCommasAndQuotesInPostText_whenTextContainsSpecialCharacters() throws Exception {
        Report report = createReport(brand, ReportStatusEnum.COMPLETED);
        createReview(report, "hello, \"world\"", new BigDecimal("3.0"),
                EmotionEnum.JOY, AspectEnum.PRODUCT, LanguageEnum.EN);

        String body = mockMvc.perform(get("/api/reports/" + report.getReportId() + "/export")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(body).contains("\"hello, \"\"world\"\"\",3.0,JOY,PRODUCT,EN,");
    }

    @Test
    void shouldFilterByEmotionAndExcludeIrrelevant_whenAnalysisFilterPresent() throws Exception {
        Report report = createReport(brand, ReportStatusEnum.COMPLETED);
        createReview(report, "happy", new BigDecimal("4.0"),
                EmotionEnum.JOY, AspectEnum.PRODUCT, LanguageEnum.EN);
        createReview(report, "angry", new BigDecimal("1.5"),
                EmotionEnum.ANGER, AspectEnum.SERVICE, LanguageEnum.EN);
        createIrrelevantPost(report, "spam", IrrelevanceReasonEnum.SPAM);

        String body = mockMvc.perform(get("/api/reports/" + report.getReportId() + "/export")
                        .param("emotion", "JOY")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        String[] lines = body.split("\\r?\\n");
        assertThat(lines).hasSize(2);
        assertThat(body).contains("happy,4.0,JOY,PRODUCT,EN,");
        assertThat(body).doesNotContain("angry");
        assertThat(body).doesNotContain("spam");
    }

    @Test
    void shouldFilterByDateRange_acrossBothRelevantAndIrrelevant() throws Exception {
        Report report = createReport(brand, ReportStatusEnum.COMPLETED);
        Review keep = createReview(report, "in range", new BigDecimal("3.0"),
                EmotionEnum.JOY, AspectEnum.PRODUCT, LanguageEnum.EN);
        Review tooOld = createReview(report, "too old", new BigDecimal("4.0"),
                EmotionEnum.JOY, AspectEnum.PRODUCT, LanguageEnum.EN);
        Post irrelevantKeep = createIrrelevantPost(report, "spam in range",
                IrrelevanceReasonEnum.SPAM);
        Post irrelevantOld = createIrrelevantPost(report, "old spam",
                IrrelevanceReasonEnum.SPAM);

        setPostCreatedAt(keep.getPost().getPostId(), "2026-03-15 12:00:00");
        setPostCreatedAt(tooOld.getPost().getPostId(), "2026-01-01 12:00:00");
        setPostCreatedAt(irrelevantKeep.getPostId(), "2026-03-20 12:00:00");
        setPostCreatedAt(irrelevantOld.getPostId(), "2026-01-05 12:00:00");

        String body = mockMvc.perform(get("/api/reports/" + report.getReportId() + "/export")
                        .param("dateFrom", "2026-03-01")
                        .param("dateTo", "2026-03-31")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(body).contains("in range");
        assertThat(body).contains("spam in range");
        assertThat(body).doesNotContain("too old");
        assertThat(body).doesNotContain("old spam");
    }

    @Test
    void shouldReturnHeaderOnly_whenReportHasNoPosts() throws Exception {
        Report report = createReport(brand, ReportStatusEnum.COMPLETED);

        mockMvc.perform(get("/api/reports/" + report.getReportId() + "/export")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(content().string(
                        "post_text,sentiment_score,emotion,aspect,language,created_at\r\n"));
    }

    @Test
    void shouldReturn400_whenReportStatusIsProcessing() throws Exception {
        Report report = createReport(brand, ReportStatusEnum.PROCESSING);

        mockMvc.perform(get("/api/reports/" + report.getReportId() + "/export")
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

        mockMvc.perform(get("/api/reports/" + report.getReportId() + "/export")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldReturn401_whenUnauthenticated() throws Exception {
        Report report = createReport(brand, ReportStatusEnum.COMPLETED);

        mockMvc.perform(get("/api/reports/" + report.getReportId() + "/export"))
                .andExpect(status().isUnauthorized());
    }

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

    private Review createReview(Report report, String postText, BigDecimal score,
                                EmotionEnum emotion, AspectEnum aspect, LanguageEnum language) {
        Post post = Post.builder()
                .report(report)
                .postText(postText)
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

    private void setPostCreatedAt(Long postId, String timestamp) {
        jdbcTemplate.update(
                "UPDATE post SET created_at = ? WHERE post_id = ?",
                Timestamp.valueOf(timestamp), postId);
    }
}
