package com.hawa.hawa_backend.features;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;

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
class ViewReportOverviewTest {

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
                .andExpect(jsonPath("$.aspects.length()").value(4))
                .andExpect(jsonPath("$.aspects[0].aspect").value("PRODUCT"))
                .andExpect(jsonPath("$.aspects[0].postCount").value(2))
                .andExpect(jsonPath("$.aspects[0].averageSentiment").value(3.5))
                .andExpect(jsonPath("$.aspects[0].emotionDistribution.JOY").value(2))
                .andExpect(jsonPath("$.aspects[1].aspect").value("SERVICE"))
                .andExpect(jsonPath("$.aspects[1].postCount").value(1))
                .andExpect(jsonPath("$.aspects[1].emotionDistribution.ANGER").value(1));
    }

    @Test
    void shouldReportFilteredOutCountAndExcludeIrrelevant_whenReportHasIrrelevantPosts() throws Exception {
        Report report = createReport(brand, ReportStatusEnum.COMPLETED);
        createReview(report, new BigDecimal("4.0"), EmotionEnum.JOY, AspectEnum.PRODUCT);
        createReview(report, new BigDecimal("2.0"), EmotionEnum.ANGER, AspectEnum.SERVICE);
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
                .andExpect(jsonPath("$.aspects.length()").value(4))
                .andExpect(jsonPath("$.aspects[?(@.aspect=='PRODUCT')].postCount").value(1))
                .andExpect(jsonPath("$.aspects[?(@.aspect=='SERVICE')].postCount").value(1));
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
                .andExpect(jsonPath("$.aspects.length()").value(4))
                .andExpect(jsonPath("$.aspects[0].postCount").value(0))
                .andExpect(jsonPath("$.aspects[0].averageSentiment").doesNotExist());
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
}
