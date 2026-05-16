package com.hawa.hawa_backend.features;

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
import com.hawa.hawa_backend.enums.LanguageEnum;
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

import java.math.BigDecimal;

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
class ViewBrandStatusTest {

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
    }

    @Test
    void shouldAggregateAcrossAllCompletedReports_whenBrandHasMultipleReports() throws Exception {
        Brand brand = createBrand("Nike", company);

        Report r1 = createReport(brand, ReportStatusEnum.COMPLETED);
        createReview(r1, new BigDecimal("4.0"), EmotionEnum.JOY, AspectEnum.PRODUCT);
        createReview(r1, new BigDecimal("1.0"), EmotionEnum.ANGER, AspectEnum.SERVICE);

        Report r2 = createReport(brand, ReportStatusEnum.COMPLETED);
        createReview(r2, new BigDecimal("5.0"), EmotionEnum.JOY, AspectEnum.PRODUCT);
        createReview(r2, new BigDecimal("3.0"), EmotionEnum.NEUTRAL, AspectEnum.PRODUCT);
        createReview(r2, new BigDecimal("2.5"), EmotionEnum.NEUTRAL, AspectEnum.DELIVERY);

        mockMvc.perform(get("/api/brands/" + brand.getBrandId() + "/status-indicator")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.brandId").value(brand.getBrandId()))
                .andExpect(jsonPath("$.brandName").value("Nike"))
                .andExpect(jsonPath("$.completedReportCount").value(2))
                .andExpect(jsonPath("$.analyzedPostCount").value(5))
                .andExpect(jsonPath("$.averageSentiment").value(3.1))
                .andExpect(jsonPath("$.sentimentBreakdown.negative.count").value(1))
                .andExpect(jsonPath("$.sentimentBreakdown.neutral.count").value(2))
                .andExpect(jsonPath("$.sentimentBreakdown.positive.count").value(2))
                .andExpect(jsonPath("$.dominantEmotion").value("JOY"))
                .andExpect(jsonPath("$.topEmotions[0].emotion").value("JOY"))
                .andExpect(jsonPath("$.topEmotions[0].count").value(2))
                .andExpect(jsonPath("$.topEmotions[1].emotion").value("NEUTRAL"))
                .andExpect(jsonPath("$.topEmotions[1].count").value(2))
                .andExpect(jsonPath("$.topEmotions[2].emotion").value("ANGER"))
                .andExpect(jsonPath("$.topAspects[0].aspect").value("PRODUCT"))
                .andExpect(jsonPath("$.topAspects[0].count").value(3))
                .andExpect(jsonPath("$.topAspects[1].aspect").value("SERVICE"))
                .andExpect(jsonPath("$.topAspects[2].aspect").value("DELIVERY"));
    }

    @Test
    void shouldExcludeNonCompletedReports_whenAggregating() throws Exception {
        Brand brand = createBrand("Nike", company);
        Report completed = createReport(brand, ReportStatusEnum.COMPLETED);
        createReview(completed, new BigDecimal("4.0"), EmotionEnum.JOY, AspectEnum.PRODUCT);

        Report processing = createReport(brand, ReportStatusEnum.PROCESSING);
        createReview(processing, new BigDecimal("1.0"), EmotionEnum.ANGER, AspectEnum.SERVICE);
        Report failed = createReport(brand, ReportStatusEnum.FAILED);
        createReview(failed, new BigDecimal("1.5"), EmotionEnum.SADNESS, AspectEnum.DELIVERY);

        mockMvc.perform(get("/api/brands/" + brand.getBrandId() + "/status-indicator")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.completedReportCount").value(1))
                .andExpect(jsonPath("$.analyzedPostCount").value(1))
                .andExpect(jsonPath("$.averageSentiment").value(4.0))
                .andExpect(jsonPath("$.dominantEmotion").value("JOY"));
    }

    @Test
    void shouldNotMixDataAcrossBrands_whenSameCompanyHasMultipleBrands() throws Exception {
        Brand nike = createBrand("Nike", company);
        Brand adidas = createBrand("Adidas", company);

        Report nikeReport = createReport(nike, ReportStatusEnum.COMPLETED);
        createReview(nikeReport, new BigDecimal("4.0"), EmotionEnum.JOY, AspectEnum.PRODUCT);

        Report adidasReport = createReport(adidas, ReportStatusEnum.COMPLETED);
        createReview(adidasReport, new BigDecimal("1.0"), EmotionEnum.ANGER, AspectEnum.SERVICE);
        createReview(adidasReport, new BigDecimal("2.0"), EmotionEnum.SADNESS, AspectEnum.SERVICE);

        mockMvc.perform(get("/api/brands/" + nike.getBrandId() + "/status-indicator")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.analyzedPostCount").value(1))
                .andExpect(jsonPath("$.averageSentiment").value(4.0))
                .andExpect(jsonPath("$.dominantEmotion").value("JOY"));
    }

    @Test
    void shouldReturnZerosAndNulls_whenBrandHasNoCompletedReports() throws Exception {
        Brand brand = createBrand("Nike", company);

        mockMvc.perform(get("/api/brands/" + brand.getBrandId() + "/status-indicator")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.brandId").value(brand.getBrandId()))
                .andExpect(jsonPath("$.brandName").value("Nike"))
                .andExpect(jsonPath("$.completedReportCount").value(0))
                .andExpect(jsonPath("$.analyzedPostCount").value(0))
                .andExpect(jsonPath("$.averageSentiment").doesNotExist())
                .andExpect(jsonPath("$.sentimentBreakdown.negative.count").value(0))
                .andExpect(jsonPath("$.sentimentBreakdown.neutral.count").value(0))
                .andExpect(jsonPath("$.sentimentBreakdown.positive.count").value(0))
                .andExpect(jsonPath("$.dominantEmotion").doesNotExist())
                .andExpect(jsonPath("$.topEmotions.length()").value(0))
                .andExpect(jsonPath("$.topAspects.length()").value(0));
    }

    @Test
    void shouldReturn404_whenBrandDoesNotExist() throws Exception {
        mockMvc.perform(get("/api/brands/99999/status-indicator")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldReturn404_whenBrandBelongsToDifferentCompany() throws Exception {
        Company otherCompany = new Company();
        otherCompany.setCompanyName("Other Corp");
        otherCompany = companyRepository.save(otherCompany);
        Brand otherBrand = createBrand("Adidas", otherCompany);

        mockMvc.perform(get("/api/brands/" + otherBrand.getBrandId() + "/status-indicator")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldReturn401_whenUnauthenticated() throws Exception {
        Brand brand = createBrand("Nike", company);

        mockMvc.perform(get("/api/brands/" + brand.getBrandId() + "/status-indicator"))
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

    private Brand createBrand(String name, Company targetCompany) {
        Brand brand = Brand.builder()
                .brandName(name)
                .company(targetCompany)
                .build();
        return brandRepository.save(brand);
    }

    private Report createReport(Brand brand, ReportStatusEnum status) {
        Report report = Report.builder()
                .brand(brand)
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
}
