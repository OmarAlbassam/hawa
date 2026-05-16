package com.hawa.hawa_backend.features;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
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
class ViewReportPostsTest {

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
    void shouldReturnIrrelevantPosts_whenRelevanceParamIsIrrelevant() throws Exception {
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
    void shouldFilterByEmotion_whenEmotionParamProvided() throws Exception {
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
    void shouldFilterBySentimentRange_whenSentimentMinAndMaxProvided() throws Exception {
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
    void shouldFilterByLanguage_whenLanguageParamProvided() throws Exception {
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
    void shouldFilterByDateRange_whenDateFromAndDateToProvided() throws Exception {
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
    void shouldFilterIrrelevantByDateRange_whenRelevanceAndDateProvided() throws Exception {
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
    void shouldSortIrrelevantByCreatedAt_whenRelevanceAndSortProvided() throws Exception {
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
    void shouldIncludeCreatedAtOnResponse_whenPostsReturned() throws Exception {
        Report report = createReport(brand, ReportStatusEnum.COMPLETED);
        createReview(report, new BigDecimal("4.0"),
                EmotionEnum.JOY, AspectEnum.PRODUCT, LanguageEnum.EN);

        mockMvc.perform(get("/api/reports/" + report.getReportId() + "/posts")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].createdAt").exists());
    }

    @Test
    void shouldSortByCreatedAtDesc_whenSortParamProvided() throws Exception {
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
    void shouldSortByScoreAsc_whenSortParamProvided() throws Exception {
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
