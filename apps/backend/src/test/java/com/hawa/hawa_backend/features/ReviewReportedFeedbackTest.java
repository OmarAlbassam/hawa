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
class ReviewReportedFeedbackTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private CompanyRepository companyRepository;
    @Autowired private BrandRepository brandRepository;
    @Autowired private ReportRepository reportRepository;
    @Autowired private PostRepository postRepository;
    @Autowired private ReviewRepository reviewRepository;
    @Autowired private FeedbackRepository feedbackRepository;
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

        adminUser = User.builder()
                .firstName("Jane")
                .lastName("Doe")
                .email("admin@example.com")
                .password(passwordEncoder.encode("Password1"))
                .company(null)
                .role(UserRoleEnum.ADMIN)
                .build();
        adminUser = userRepository.save(adminUser);
        adminToken = jwtService.generateAccessToken(adminUser);
    }

    @Test
    void shouldReturnReportedReviewsWithFullContext_whenAdminRequests() throws Exception {
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

        User reporter = createUser("reporter@example.com", UserRoleEnum.MARKETING_USER, companyId);

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
        User marketingUser = createUser("marketing@example.com",
                UserRoleEnum.MARKETING_USER, companyId);
        String marketingToken = jwtService.generateAccessToken(marketingUser);

        mockMvc.perform(get("/api/admin/reported-reviews")
                        .header("Authorization", "Bearer " + marketingToken))
                .andExpect(status().isForbidden());
    }

    private User createUser(String email, UserRoleEnum role, Long targetCompanyId) {
        Company company = targetCompanyId == null ? null
                : companyRepository.findById(targetCompanyId).orElseThrow();
        User user = User.builder()
                .firstName("Jane")
                .lastName("Doe")
                .email(email)
                .password(passwordEncoder.encode("Password1"))
                .company(company)
                .role(role)
                .build();
        return userRepository.save(user);
    }
}
