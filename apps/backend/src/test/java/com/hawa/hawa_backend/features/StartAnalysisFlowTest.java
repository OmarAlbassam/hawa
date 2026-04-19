package com.hawa.hawa_backend.features;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

import com.hawa.hawa_backend.TestcontainersConfiguration;
import com.hawa.hawa_backend.auth.JwtService;
import com.hawa.hawa_backend.brand.Brand;
import com.hawa.hawa_backend.brand.BrandRepository;
import com.hawa.hawa_backend.company.Company;
import com.hawa.hawa_backend.company.CompanyRepository;
import com.hawa.hawa_backend.enums.AspectEnum;
import com.hawa.hawa_backend.enums.EmotionEnum;
import com.hawa.hawa_backend.enums.KeywordTypeEnum;
import com.hawa.hawa_backend.enums.LanguageEnum;
import com.hawa.hawa_backend.enums.ReportStatusEnum;
import com.hawa.hawa_backend.enums.UserRoleEnum;
import com.hawa.hawa_backend.keyword.Keyword;
import com.hawa.hawa_backend.keyword.KeywordRepository;
import com.hawa.hawa_backend.llm.LlmClient;
import com.hawa.hawa_backend.llm.LlmServiceException;
import com.hawa.hawa_backend.llm.dto.AnalyzeResult;
import com.hawa.hawa_backend.llm.dto.BatchAnalyzeRequest;
import com.hawa.hawa_backend.llm.dto.BatchAnalyzeResponse;
import com.hawa.hawa_backend.post.Post;
import com.hawa.hawa_backend.post.collector.RedditPostCollector;
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
class StartAnalysisFlowTest {

    private static final Duration JOB_TIMEOUT = Duration.ofSeconds(5);

    @Autowired private MockMvc mockMvc;
    @Autowired private CompanyRepository companyRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private BrandRepository brandRepository;
    @Autowired private KeywordRepository keywordRepository;
    @Autowired private ReportRepository reportRepository;
    @Autowired private ReviewRepository reviewRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JwtService jwtService;
    @Autowired private JdbcTemplate jdbcTemplate;

    @MockitoBean private LlmClient llmClient;
    @MockitoSpyBean private RedditPostCollector redditPostCollector;

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

        marketingUser = User.builder()
                .firstName("Test")
                .lastName("User")
                .email("marketing@example.com")
                .password(passwordEncoder.encode("Password1"))
                .company(company)
                .role(UserRoleEnum.MARKETING_USER)
                .build();
        marketingUser = userRepository.save(marketingUser);
        userToken = jwtService.generateAccessToken(marketingUser);

        brand = Brand.builder()
                .brandName("Nike")
                .company(company)
                .industry("Sportswear")
                .build();
        brand = brandRepository.save(brand);
    }

    @Test
    void shouldCompleteReport_andPersistReviews_whenLlmSucceeds() throws Exception {
        seedKeyword("jordan", KeywordTypeEnum.PRODUCT);
        seedKeyword("nike", KeywordTypeEnum.BRAND_NAME);

        doAnswer(inv -> List.of(
                        buildPost(inv.getArgument(0), "Great shoes!"),
                        buildPost(inv.getArgument(0), "Terrible delivery time")))
                .when(redditPostCollector).collect(any(), any(), any(), any());

        ArgumentCaptor<BatchAnalyzeRequest> captor = ArgumentCaptor.forClass(BatchAnalyzeRequest.class);
        when(llmClient.analyzeBatch(captor.capture())).thenAnswer(inv -> {
            BatchAnalyzeRequest req = inv.getArgument(0);
            List<AnalyzeResult> results = List.of(
                    new AnalyzeResult(req.posts().get(0).postId(), 4.0, 4.0, "JOY", "PRODUCT"),
                    new AnalyzeResult(req.posts().get(1).postId(), 2.0, 2.0, "ANGER", "DELIVERY"));
            return new BatchAnalyzeResponse(results, List.of());
        });

        String reportId = mockMvc.perform(post("/api/brands/" + brand.getBrandId() + "/reports")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"dataSource": "REDDIT"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        await().atMost(JOB_TIMEOUT).until(() ->
                reportRepository.findAll().getFirst().getStatus() == ReportStatusEnum.COMPLETED);

        Report finalReport = reportRepository.findAll().getFirst();
        assertThat(finalReport.getFinishedAt()).isNotNull();
        assertThat(finalReport.getScore()).isEqualTo(3);
        assertThat(finalReport.getSummary()).contains("analyzed=2", "failed=0");
        assertThat(finalReport.getFailureReason()).isNull();

        List<Review> reviews = reviewRepository.findAll();
        assertThat(reviews).hasSize(2);
        assertThat(reviews).extracting(Review::getEmotion)
                .containsExactlyInAnyOrder(EmotionEnum.JOY, EmotionEnum.ANGER);
        assertThat(reviews).extracting(Review::getAspect)
                .containsExactlyInAnyOrder(AspectEnum.PRODUCT, AspectEnum.DELIVERY);

        BatchAnalyzeRequest sent = captor.getValue();
        assertThat(sent.brandName()).isEqualTo("Nike");
        assertThat(sent.brandIndustry()).isEqualTo("Sportswear");
        assertThat(sent.keywords()).containsExactlyInAnyOrder("jordan", "nike");
        assertThat(reportId).contains("PENDING");
    }

    @Test
    void shouldMarkReportFailed_whenLlmServiceThrows() throws Exception {
        doAnswer(inv -> List.of(buildPost(inv.getArgument(0), "anything")))
                .when(redditPostCollector).collect(any(), any(), any(), any());

        when(llmClient.analyzeBatch(any()))
                .thenThrow(new LlmServiceException("boom: connection refused"));

        mockMvc.perform(post("/api/brands/" + brand.getBrandId() + "/reports")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"dataSource": "REDDIT"}
                                """))
                .andExpect(status().isCreated());

        await().atMost(JOB_TIMEOUT).until(() ->
                reportRepository.findAll().getFirst().getStatus() == ReportStatusEnum.FAILED);

        Report finalReport = reportRepository.findAll().getFirst();
        assertThat(finalReport.getFinishedAt()).isNotNull();
        assertThat(finalReport.getFailureReason()).contains("boom");
        assertThat(finalReport.getFailureReason().length()).isLessThanOrEqualTo(500);
        assertThat(reviewRepository.count()).isZero();
    }

    @Test
    void shouldCompleteWithNoPosts_whenCollectorReturnsEmpty() throws Exception {
        doReturn(List.of())
                .when(redditPostCollector).collect(any(), any(), any(), any());

        mockMvc.perform(post("/api/brands/" + brand.getBrandId() + "/reports")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"dataSource": "REDDIT"}
                                """))
                .andExpect(status().isCreated());

        await().atMost(JOB_TIMEOUT).until(() ->
                reportRepository.findAll().getFirst().getStatus() == ReportStatusEnum.COMPLETED);

        Report finalReport = reportRepository.findAll().getFirst();
        assertThat(finalReport.getSummary()).isEqualTo("No posts collected");
        verifyNoInteractions(llmClient);
    }

    @Test
    void shouldSendNullKeywords_whenBrandHasNone() throws Exception {
        doAnswer(inv -> List.of(buildPost(inv.getArgument(0), "hello")))
                .when(redditPostCollector).collect(any(), any(), any(), any());

        ArgumentCaptor<BatchAnalyzeRequest> captor = ArgumentCaptor.forClass(BatchAnalyzeRequest.class);
        when(llmClient.analyzeBatch(captor.capture())).thenAnswer(inv -> {
            BatchAnalyzeRequest req = inv.getArgument(0);
            return new BatchAnalyzeResponse(
                    List.of(new AnalyzeResult(req.posts().get(0).postId(), 3.0, 3.0, "JOY", "PRODUCT")),
                    List.of());
        });

        mockMvc.perform(post("/api/brands/" + brand.getBrandId() + "/reports")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"dataSource": "REDDIT"}
                                """))
                .andExpect(status().isCreated());

        await().atMost(JOB_TIMEOUT).until(() ->
                reportRepository.findAll().getFirst().getStatus() == ReportStatusEnum.COMPLETED);

        assertThat(captor.getValue().keywords()).isNull();
    }

    private void seedKeyword(String text, KeywordTypeEnum type) {
        Keyword k = Keyword.builder()
                .brand(brand)
                .keyword(text)
                .keywordType(type)
                .build();
        keywordRepository.save(k);
    }

    private static Post buildPost(Report report, String text) {
        return Post.builder()
                .report(report)
                .postText(text)
                .language(LanguageEnum.EN)
                .build();
    }
}
