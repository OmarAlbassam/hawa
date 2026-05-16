package com.hawa.hawa_backend.features;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
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
import com.hawa.hawa_backend.enums.DataSourceEnum;
import com.hawa.hawa_backend.enums.EmotionEnum;
import com.hawa.hawa_backend.enums.IrrelevanceReasonEnum;
import com.hawa.hawa_backend.enums.KeywordTypeEnum;
import com.hawa.hawa_backend.enums.LanguageEnum;
import com.hawa.hawa_backend.enums.RelevanceStatusEnum;
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
import com.hawa.hawa_backend.post.PostRepository;
import com.hawa.hawa_backend.postprovider.reddit.RedditPostProvider;
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
class StartAnalysisTest {

    private static final Duration JOB_TIMEOUT = Duration.ofSeconds(5);

    @Autowired private MockMvc mockMvc;
    @Autowired private CompanyRepository companyRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private BrandRepository brandRepository;
    @Autowired private KeywordRepository keywordRepository;
    @Autowired private ReportRepository reportRepository;
    @Autowired private ReviewRepository reviewRepository;
    @Autowired private PostRepository postRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JwtService jwtService;
    @Autowired private JdbcTemplate jdbcTemplate;

    @MockitoBean private LlmClient llmClient;
    @MockitoSpyBean private RedditPostProvider redditPostProvider;

    private Company company;
    private User marketingUser;
    private String userToken;
    private Brand brand;
    private Long brandKeywordId;

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
                .industry("Sportswear")
                .build();
        brand = brandRepository.save(brand);

        brandKeywordId = seedKeyword("nike", KeywordTypeEnum.BRAND_NAME);

        doReturn(List.of()).when(redditPostProvider).collect(any(), any(), any(), any());
    }

    @Nested
    class WhenStartingAnalysis {

        @Test
        void shouldCreateReport_whenRequestIsValid() throws Exception {
            mockMvc.perform(post("/api/brands/" + brand.getBrandId() + "/reports")
                            .header("Authorization", "Bearer " + userToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(String.format(
                                    "{\"dataSource\": \"REDDIT\", \"selectedKeywordIds\": [%d], \"maxPosts\": 50}",
                                    brandKeywordId)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.reportId").isNumber())
                    .andExpect(jsonPath("$.brandName").value("Nike"))
                    .andExpect(jsonPath("$.status").value("QUEUED"))
                    .andExpect(jsonPath("$.dataSource").value("REDDIT"))
                    .andExpect(jsonPath("$.createdAt").isNotEmpty());
        }

        @Test
        void shouldCreateReport_whenRequestIncludesDateRange() throws Exception {
            mockMvc.perform(post("/api/brands/" + brand.getBrandId() + "/reports")
                            .header("Authorization", "Bearer " + userToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(String.format("""
                                    {
                                        "dataSource": "CSV_UPLOAD",
                                        "dateFrom": "2026-01-01",
                                        "dateTo": "2026-03-31",
                                        "selectedKeywordIds": [%d],
                                        "maxPosts": 50
                                    }
                                    """, brandKeywordId)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.dataSource").value("CSV_UPLOAD"))
                    .andExpect(jsonPath("$.dateFrom").value("2026-01-01"))
                    .andExpect(jsonPath("$.dateTo").value("2026-03-31"));
        }

        @Test
        void shouldReject404_whenBrandNotFound() throws Exception {
            mockMvc.perform(post("/api/brands/99999/reports")
                            .header("Authorization", "Bearer " + userToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(String.format(
                                    "{\"dataSource\": \"REDDIT\", \"selectedKeywordIds\": [%d], \"maxPosts\": 50}",
                                    brandKeywordId)))
                    .andExpect(status().isNotFound());
        }

        @Test
        void shouldReject400_whenBrandBelongsToOtherCompany() throws Exception {
            Company otherCompany = new Company();
            otherCompany.setCompanyName("Other Corp");
            otherCompany = companyRepository.save(otherCompany);

            Brand otherBrand = Brand.builder()
                    .brandName("Adidas")
                    .company(otherCompany)
                    .build();
            otherBrand = brandRepository.save(otherBrand);

            mockMvc.perform(post("/api/brands/" + otherBrand.getBrandId() + "/reports")
                            .header("Authorization", "Bearer " + userToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(String.format(
                                    "{\"dataSource\": \"REDDIT\", \"selectedKeywordIds\": [%d], \"maxPosts\": 50}",
                                    brandKeywordId)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        void shouldReject400_whenDataSourceMissing() throws Exception {
            mockMvc.perform(post("/api/brands/" + brand.getBrandId() + "/reports")
                            .header("Authorization", "Bearer " + userToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(String.format(
                                    "{\"selectedKeywordIds\": [%d]}", brandKeywordId)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        void shouldReject400_whenSelectedKeywordIdsOmitted() throws Exception {
            mockMvc.perform(post("/api/brands/" + brand.getBrandId() + "/reports")
                            .header("Authorization", "Bearer " + userToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"dataSource": "REDDIT"}
                                    """))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(llmClient);
            assertThat(reportRepository.count()).isZero();
        }

        @Test
        void shouldReject400_whenSelectedKeywordIdsEmpty() throws Exception {
            mockMvc.perform(post("/api/brands/" + brand.getBrandId() + "/reports")
                            .header("Authorization", "Bearer " + userToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"dataSource": "REDDIT", "selectedKeywordIds": []}
                                    """))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(llmClient);
            assertThat(reportRepository.count()).isZero();
        }

        @Test
        void shouldReject400_whenSelectedKeywordBelongsToAnotherBrand() throws Exception {
            Brand otherBrand = brandRepository.save(Brand.builder()
                    .brandName("Other")
                    .company(company)
                    .industry("Other")
                    .build());
            Long foreignKeywordId = seedKeyword(otherBrand, "foreign", KeywordTypeEnum.PRODUCT);

            mockMvc.perform(post("/api/brands/" + brand.getBrandId() + "/reports")
                            .header("Authorization", "Bearer " + userToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(String.format(
                                    "{\"dataSource\": \"REDDIT\", \"selectedKeywordIds\": [%d], \"maxPosts\": 50}",
                                    foreignKeywordId)))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(llmClient);
            assertThat(reportRepository.count()).isZero();
        }
    }

    @Nested
    class WhenCheckingStatus {

        @Test
        void shouldReturnReportStatus_whenReportExists() throws Exception {
            Report report = Report.builder()
                    .brand(brand)
                    .user(marketingUser)
                    .dataSource(DataSourceEnum.REDDIT)
                    .status(ReportStatusEnum.PROCESSING)
                    .build();
            report = reportRepository.save(report);

            mockMvc.perform(get("/api/reports/" + report.getReportId() + "/status")
                            .header("Authorization", "Bearer " + userToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.reportId").value(report.getReportId()))
                    .andExpect(jsonPath("$.status").value("PROCESSING"))
                    .andExpect(jsonPath("$.createdAt").isNotEmpty());
        }

        @Test
        void shouldReject404_whenReportNotFound() throws Exception {
            mockMvc.perform(get("/api/reports/99999/status")
                            .header("Authorization", "Bearer " + userToken))
                    .andExpect(status().isNotFound());
        }

        @Test
        void shouldReject400_whenReportBelongsToOtherCompany() throws Exception {
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

            mockMvc.perform(get("/api/reports/" + report.getReportId() + "/status")
                            .header("Authorization", "Bearer " + userToken))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    class WhenJobProcesses {

        @Test
        void shouldCompleteReportAndPersistReviews_whenLlmSucceeds() throws Exception {
            Long jordanId = seedKeyword("jordan", KeywordTypeEnum.PRODUCT);
            Long ignoredId = seedKeyword("airmax", KeywordTypeEnum.PRODUCT);

            doAnswer(inv -> List.of(
                            buildPost(inv.getArgument(0), "Great shoes!"),
                            buildPost(inv.getArgument(0), "Terrible delivery time")))
                    .when(redditPostProvider).collect(any(), any(), any(), any());

            ArgumentCaptor<BatchAnalyzeRequest> captor = ArgumentCaptor.forClass(BatchAnalyzeRequest.class);
            when(llmClient.analyzeBatch(captor.capture())).thenAnswer(inv -> {
                BatchAnalyzeRequest req = inv.getArgument(0);
                List<AnalyzeResult> results = List.of(
                        new AnalyzeResult(req.posts().get(0).postId(), true, null, 4.0, 4.0, "JOY", "PRODUCT"),
                        new AnalyzeResult(req.posts().get(1).postId(), true, null, 2.0, 2.0, "ANGER", "DELIVERY"));
                return new BatchAnalyzeResponse(results, List.of());
            });

            String reportId = mockMvc.perform(post("/api/brands/" + brand.getBrandId() + "/reports")
                            .header("Authorization", "Bearer " + userToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(String.format(
                                    "{\"dataSource\": \"REDDIT\", \"selectedKeywordIds\": [%d, %d], \"maxPosts\": 50}",
                                    jordanId, brandKeywordId)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.status").value("QUEUED"))
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
            assertThat(reportId).contains("QUEUED");
            assertThat(sent.keywords()).doesNotContain("airmax");
            assertThat(ignoredId).isNotNull();
        }

        @Test
        void shouldMarkReportFailed_whenLlmServiceThrows() throws Exception {
            doAnswer(inv -> List.of(buildPost(inv.getArgument(0), "anything")))
                    .when(redditPostProvider).collect(any(), any(), any(), any());

            when(llmClient.analyzeBatch(any()))
                    .thenThrow(new LlmServiceException("boom: connection refused"));

            mockMvc.perform(post("/api/brands/" + brand.getBrandId() + "/reports")
                            .header("Authorization", "Bearer " + userToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(String.format(
                                    "{\"dataSource\": \"REDDIT\", \"selectedKeywordIds\": [%d], \"maxPosts\": 50}",
                                    brandKeywordId)))
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
                    .when(redditPostProvider).collect(any(), any(), any(), any());

            mockMvc.perform(post("/api/brands/" + brand.getBrandId() + "/reports")
                            .header("Authorization", "Bearer " + userToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(String.format(
                                    "{\"dataSource\": \"REDDIT\", \"selectedKeywordIds\": [%d], \"maxPosts\": 50}",
                                    brandKeywordId)))
                    .andExpect(status().isCreated());

            await().atMost(JOB_TIMEOUT).until(() ->
                    reportRepository.findAll().getFirst().getStatus() == ReportStatusEnum.COMPLETED);

            Report finalReport = reportRepository.findAll().getFirst();
            assertThat(finalReport.getSummary()).isEqualTo("No posts collected");
            verifyNoInteractions(llmClient);
        }

        @Test
        void shouldPartitionIrrelevantPostsFromRelevantReviews_whenLlmVerdictIsMixed() throws Exception {
            doAnswer(inv -> List.of(
                            buildPost(inv.getArgument(0), "Love the new Air Jordans"),
                            buildPost(inv.getArgument(0), "unrelated homonym"),
                            buildPost(inv.getArgument(0), "buy crypto now!!!")))
                    .when(redditPostProvider).collect(any(), any(), any(), any());

            when(llmClient.analyzeBatch(any())).thenAnswer(inv -> {
                BatchAnalyzeRequest req = inv.getArgument(0);
                List<AnalyzeResult> results = List.of(
                        new AnalyzeResult(req.posts().get(0).postId(), true, null, 4.5, 4.5, "JOY", "PRODUCT"),
                        new AnalyzeResult(req.posts().get(1).postId(), false, "HOMONYM", null, null, null, null),
                        new AnalyzeResult(req.posts().get(2).postId(), false, "SPAM", null, null, null, null));
                return new BatchAnalyzeResponse(results, List.of());
            });

            mockMvc.perform(post("/api/brands/" + brand.getBrandId() + "/reports")
                            .header("Authorization", "Bearer " + userToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(String.format(
                                    "{\"dataSource\": \"REDDIT\", \"selectedKeywordIds\": [%d], \"maxPosts\": 50}",
                                    brandKeywordId)))
                    .andExpect(status().isCreated());

            await().atMost(JOB_TIMEOUT).until(() ->
                    reportRepository.findAll().getFirst().getStatus() == ReportStatusEnum.COMPLETED);

            List<Review> reviews = reviewRepository.findAll();
            assertThat(reviews).hasSize(1);
            assertThat(reviews.getFirst().getEmotion()).isEqualTo(EmotionEnum.JOY);
            assertThat(reviews.getFirst().getAspect()).isEqualTo(AspectEnum.PRODUCT);

            List<Post> irrelevant = postRepository.findAll().stream()
                    .filter(p -> p.getRelevanceStatus() == RelevanceStatusEnum.IRRELEVANT)
                    .toList();
            assertThat(irrelevant).hasSize(2);
            assertThat(irrelevant).extracting(Post::getIrrelevanceReason)
                    .containsExactlyInAnyOrder(IrrelevanceReasonEnum.HOMONYM, IrrelevanceReasonEnum.SPAM);

            Report finalReport = reportRepository.findAll().getFirst();
            assertThat(finalReport.getScore()).isEqualTo(5);
            assertThat(finalReport.getSummary())
                    .contains("analyzed=1", "filtered_out=2", "failed=0");
        }
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

    private Long seedKeyword(String text, KeywordTypeEnum type) {
        return seedKeyword(brand, text, type);
    }

    private Long seedKeyword(Brand ownerBrand, String text, KeywordTypeEnum type) {
        Keyword k = Keyword.builder()
                .brand(ownerBrand)
                .keyword(text)
                .keywordType(type)
                .build();
        return keywordRepository.save(k).getKeywordId();
    }

    private static Post buildPost(Report report, String text) {
        return Post.builder()
                .report(report)
                .postText(text)
                .language(LanguageEnum.EN)
                .build();
    }
}
