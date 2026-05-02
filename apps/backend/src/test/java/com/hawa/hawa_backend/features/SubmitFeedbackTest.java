package com.hawa.hawa_backend.features;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
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

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class SubmitFeedbackTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private CompanyRepository companyRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private BrandRepository brandRepository;
    @Autowired private ReportRepository reportRepository;
    @Autowired private PostRepository postRepository;
    @Autowired private ReviewRepository reviewRepository;
    @Autowired private FeedbackRepository feedbackRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JwtService jwtService;
    @Autowired private JdbcTemplate jdbcTemplate;

    private Company company;
    private User marketingUser;
    private String userToken;
    private Review review;

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
        company.setCompanyName("Acme Corp");
        company = companyRepository.save(company);

        marketingUser = userRepository.save(User.builder()
                .firstName("Mark")
                .lastName("Tester")
                .email("marketing@example.com")
                .password(passwordEncoder.encode("Password1"))
                .company(company)
                .role(UserRoleEnum.MARKETING_USER)
                .build());
        userToken = jwtService.generateAccessToken(marketingUser);

        Brand brand = brandRepository.save(Brand.builder()
                .brandName("Nike")
                .company(company)
                .industry("Sportswear")
                .build());

        Report report = reportRepository.save(Report.builder()
                .user(marketingUser)
                .brand(brand)
                .dataSource(DataSourceEnum.REDDIT)
                .status(ReportStatusEnum.COMPLETED)
                .build());

        Post post = postRepository.save(Post.builder()
                .report(report)
                .postText("Great product, terrible delivery!")
                .language(LanguageEnum.EN)
                .build());

        review = reviewRepository.save(Review.builder()
                .post(post)
                .score(new BigDecimal("4.0"))
                .llmScore(new BigDecimal("4.0"))
                .emotion(EmotionEnum.JOY)
                .aspect(AspectEnum.PRODUCT)
                .build());
    }

    @Nested
    class WhenValidRequest {

        @Test
        void shouldReturn201_andPersistFeedback_whenSubmittedFirstTime() throws Exception {
            mockMvc.perform(post("/api/reviews/" + review.getReviewId() + "/feedback")
                            .header("Authorization", "Bearer " + userToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"brief": "The aspect should be DELIVERY, not PRODUCT"}
                                    """))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.feedbackId").exists())
                    .andExpect(jsonPath("$.reviewId").value(review.getReviewId()))
                    .andExpect(jsonPath("$.brief").value("The aspect should be DELIVERY, not PRODUCT"));

            List<Feedback> all = feedbackRepository.findAll();
            assertThat(all).hasSize(1);
            Feedback saved = all.getFirst();
            assertThat(saved.getReview().getReviewId()).isEqualTo(review.getReviewId());
            assertThat(saved.getUser().getUserId()).isEqualTo(marketingUser.getUserId());
            assertThat(saved.getBrief()).isEqualTo("The aspect should be DELIVERY, not PRODUCT");
        }

        @Test
        void shouldStoreTrimmedBrief_whenSubmittedWithSurroundingWhitespace() throws Exception {
            mockMvc.perform(post("/api/reviews/" + review.getReviewId() + "/feedback")
                            .header("Authorization", "Bearer " + userToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"brief": "   wrong aspect classification   "}
                                    """))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.brief").value("wrong aspect classification"));

            assertThat(feedbackRepository.findAll().getFirst().getBrief())
                    .isEqualTo("wrong aspect classification");
        }

        @Test
        void shouldUpdateExistingFeedback_whenSameUserSubmitsTwice() throws Exception {
            // First submission
            mockMvc.perform(post("/api/reviews/" + review.getReviewId() + "/feedback")
                            .header("Authorization", "Bearer " + userToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"brief": "first version of explanation"}
                                    """))
                    .andExpect(status().isCreated());

            // Second submission overwrites the brief
            mockMvc.perform(post("/api/reviews/" + review.getReviewId() + "/feedback")
                            .header("Authorization", "Bearer " + userToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"brief": "updated explanation after rethinking"}
                                    """))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.brief").value("updated explanation after rethinking"));

            List<Feedback> all = feedbackRepository.findAll();
            assertThat(all).hasSize(1);
            assertThat(all.getFirst().getBrief()).isEqualTo("updated explanation after rethinking");
        }
    }

    @Nested
    class WhenInvalidInput {

        @Test
        void shouldReturn400_whenBriefIsBlank() throws Exception {
            mockMvc.perform(post("/api/reviews/" + review.getReviewId() + "/feedback")
                            .header("Authorization", "Bearer " + userToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"brief": "   "}
                                    """))
                    .andExpect(status().isBadRequest());

            assertThat(feedbackRepository.count()).isZero();
        }

        @Test
        void shouldReturn400_whenBriefMissing() throws Exception {
            mockMvc.perform(post("/api/reviews/" + review.getReviewId() + "/feedback")
                            .header("Authorization", "Bearer " + userToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isBadRequest());

            assertThat(feedbackRepository.count()).isZero();
        }

        @Test
        void shouldReturn400_whenBriefBelowMinLength() throws Exception {
            mockMvc.perform(post("/api/reviews/" + review.getReviewId() + "/feedback")
                            .header("Authorization", "Bearer " + userToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"brief": "abc"}
                                    """))
                    .andExpect(status().isBadRequest());

            assertThat(feedbackRepository.count()).isZero();
        }

        @Test
        void shouldReturn400_whenBriefIsBelowMinLengthAfterTrim() throws Exception {
            // " abcd " — 6 chars raw, but only 4 after trim → should fail @Size(min=5)
            mockMvc.perform(post("/api/reviews/" + review.getReviewId() + "/feedback")
                            .header("Authorization", "Bearer " + userToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"brief": "  abcd  "}
                                    """))
                    .andExpect(status().isBadRequest());

            assertThat(feedbackRepository.count()).isZero();
        }

        @Test
        void shouldReturn400_whenBriefExceedsMaxLength() throws Exception {
            String tooLong = "x".repeat(1001);
            mockMvc.perform(post("/api/reviews/" + review.getReviewId() + "/feedback")
                            .header("Authorization", "Bearer " + userToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"brief\": \"" + tooLong + "\"}"))
                    .andExpect(status().isBadRequest());

            assertThat(feedbackRepository.count()).isZero();
        }
    }

    @Nested
    class WhenReviewNotAccessible {

        @Test
        void shouldReturn404_whenReviewDoesNotExist() throws Exception {
            mockMvc.perform(post("/api/reviews/999999/feedback")
                            .header("Authorization", "Bearer " + userToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"brief": "this review id does not exist"}
                                    """))
                    .andExpect(status().isNotFound());

            assertThat(feedbackRepository.count()).isZero();
        }

        @Test
        void shouldReturn404_whenReviewBelongsToAnotherCompany() throws Exception {
            Company otherCompany = new Company();
            otherCompany.setCompanyName("Other Corp");
            otherCompany = companyRepository.save(otherCompany);

            Brand otherBrand = brandRepository.save(Brand.builder()
                    .brandName("OtherBrand")
                    .company(otherCompany)
                    .industry("Tech")
                    .build());

            User otherUser = userRepository.save(User.builder()
                    .firstName("Other")
                    .lastName("Owner")
                    .email("other@example.com")
                    .password(passwordEncoder.encode("Password1"))
                    .company(otherCompany)
                    .role(UserRoleEnum.MARKETING_USER)
                    .build());

            Report otherReport = reportRepository.save(Report.builder()
                    .user(otherUser)
                    .brand(otherBrand)
                    .dataSource(DataSourceEnum.REDDIT)
                    .status(ReportStatusEnum.COMPLETED)
                    .build());

            Post otherPost = postRepository.save(Post.builder()
                    .report(otherReport)
                    .postText("Foreign post")
                    .language(LanguageEnum.EN)
                    .build());

            Review otherReview = reviewRepository.save(Review.builder()
                    .post(otherPost)
                    .score(new BigDecimal("3.0"))
                    .emotion(EmotionEnum.JOY)
                    .aspect(AspectEnum.PRODUCT)
                    .build());

            mockMvc.perform(post("/api/reviews/" + otherReview.getReviewId() + "/feedback")
                            .header("Authorization", "Bearer " + userToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"brief": "Trying to feedback on another company's review"}
                                    """))
                    .andExpect(status().isNotFound());

            assertThat(feedbackRepository.count()).isZero();
        }
    }

    @Nested
    class WhenUnauthenticated {

        @Test
        void shouldReturn401_whenNoToken() throws Exception {
            mockMvc.perform(post("/api/reviews/" + review.getReviewId() + "/feedback")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"brief": "should not be accepted"}
                                    """))
                    .andExpect(status().isUnauthorized());

            assertThat(feedbackRepository.count()).isZero();
        }
    }
}
