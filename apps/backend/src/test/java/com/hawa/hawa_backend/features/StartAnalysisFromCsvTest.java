package com.hawa.hawa_backend.features;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.hawa.hawa_backend.TestcontainersConfiguration;
import com.hawa.hawa_backend.auth.JwtService;
import com.hawa.hawa_backend.brand.Brand;
import com.hawa.hawa_backend.brand.BrandRepository;
import com.hawa.hawa_backend.company.Company;
import com.hawa.hawa_backend.company.CompanyRepository;
import com.hawa.hawa_backend.enums.DataSourceEnum;
import com.hawa.hawa_backend.enums.LanguageEnum;
import com.hawa.hawa_backend.enums.ReportStatusEnum;
import com.hawa.hawa_backend.enums.UserRoleEnum;
import com.hawa.hawa_backend.llm.LlmClient;
import com.hawa.hawa_backend.llm.dto.AnalyzeResult;
import com.hawa.hawa_backend.llm.dto.BatchAnalyzeRequest;
import com.hawa.hawa_backend.llm.dto.BatchAnalyzeResponse;
import com.hawa.hawa_backend.post.Post;
import com.hawa.hawa_backend.post.PostRepository;
import com.hawa.hawa_backend.report.Report;
import com.hawa.hawa_backend.report.ReportRepository;
import com.hawa.hawa_backend.user.User;
import com.hawa.hawa_backend.user.UserRepository;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class StartAnalysisFromCsvTest {

    private static final Duration JOB_TIMEOUT = Duration.ofSeconds(5);

    @Autowired private org.springframework.test.web.servlet.MockMvc mockMvc;
    @Autowired private CompanyRepository companyRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private BrandRepository brandRepository;
    @Autowired private ReportRepository reportRepository;
    @Autowired private PostRepository postRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JwtService jwtService;
    @Autowired private JdbcTemplate jdbcTemplate;

    @MockitoBean private LlmClient llmClient;

    private String userToken;
    private Brand brand;
    private Company otherCompany;

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

        Company company = new Company();
        company.setCompanyName("Acme Corp");
        company = companyRepository.save(company);

        otherCompany = new Company();
        otherCompany.setCompanyName("Rival Corp");
        otherCompany = companyRepository.save(otherCompany);

        User user = User.builder()
                .firstName("Marketing")
                .lastName("User")
                .email("csv@example.com")
                .password(passwordEncoder.encode("Password1"))
                .company(company)
                .role(UserRoleEnum.MARKETING_USER)
                .build();
        user = userRepository.save(user);
        userToken = jwtService.generateAccessToken(user);

        brand = Brand.builder()
                .brandName("Nike")
                .company(company)
                .industry("Sportswear")
                .build();
        brand = brandRepository.save(brand);
    }

    @Test
    void shouldReturnTemplate_whenTemplateRequested() throws Exception {
        mockMvc.perform(get("/api/datasets/template")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition",
                        org.hamcrest.Matchers.containsString("hawa-dataset-template.csv")))
                .andExpect(content().string(org.hamcrest.Matchers.startsWith("text,url,language")));
    }

    @Test
    void shouldCreateReportAndPersistPosts_whenValidCsvUploaded() throws Exception {
        String csv = """
                text,url,language
                "I love this product","https://example.com/1",EN
                "الخدمة ممتازة",,AR
                "Delivery was slow","https://example.com/3",
                """;
        MockMultipartFile file = new MockMultipartFile(
                "file", "posts.csv", "text/csv", csv.getBytes());

        when(llmClient.analyzeBatch(any())).thenAnswer(inv -> {
            BatchAnalyzeRequest req = inv.getArgument(0);
            List<AnalyzeResult> results = req.posts().stream()
                    .map(p -> new AnalyzeResult(p.postId(), 4.0, 4.0, "JOY", "PRODUCT"))
                    .toList();
            return new BatchAnalyzeResponse(results, List.of());
        });

        mockMvc.perform(multipart("/api/brands/" + brand.getBrandId() + "/reports/csv")
                        .file(file)
                        .param("dateFrom", "2026-01-01")
                        .param("dateTo", "2026-03-01")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.dataSource").value("CSV_UPLOAD"))
                .andExpect(jsonPath("$.brandName").value("Nike"));

        assertThat(reportRepository.findAll()).hasSize(1);
        Report saved = reportRepository.findAll().getFirst();
        assertThat(saved.getDataSource()).isEqualTo(DataSourceEnum.CSV_UPLOAD);

        List<Post> posts = postRepository.findByReport_ReportId(saved.getReportId());
        assertThat(posts).hasSize(3);
        assertThat(posts).extracting(Post::getPostText)
                .containsExactlyInAnyOrder(
                        "I love this product",
                        "الخدمة ممتازة",
                        "Delivery was slow");
        assertThat(posts).extracting(Post::getLanguage)
                .containsExactlyInAnyOrder(LanguageEnum.EN, LanguageEnum.AR, LanguageEnum.EN);

        await().atMost(JOB_TIMEOUT).until(() ->
                reportRepository.findAll().getFirst().getStatus() == ReportStatusEnum.COMPLETED);
    }

    @Test
    void shouldDefaultLanguageToEnglish_whenLanguageColumnBlank() throws Exception {
        String csv = "text\n\"Hello world\"\n";
        MockMultipartFile file = new MockMultipartFile(
                "file", "posts.csv", "text/csv", csv.getBytes());

        when(llmClient.analyzeBatch(any())).thenAnswer(inv -> {
            BatchAnalyzeRequest req = inv.getArgument(0);
            return new BatchAnalyzeResponse(List.of(new AnalyzeResult(
                    req.posts().getFirst().postId(), 3.0, 3.0, "JOY", "PRODUCT")), List.of());
        });

        mockMvc.perform(multipart("/api/brands/" + brand.getBrandId() + "/reports/csv")
                        .file(file)
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isCreated());

        List<Post> posts = postRepository.findAll();
        assertThat(posts).hasSize(1);
        assertThat(posts.getFirst().getLanguage()).isEqualTo(LanguageEnum.EN);
    }

    @Test
    void shouldReturn400_whenTextColumnMissing() throws Exception {
        String csv = """
                url,language
                "https://example.com/1",EN
                """;
        MockMultipartFile file = new MockMultipartFile(
                "file", "posts.csv", "text/csv", csv.getBytes());

        mockMvc.perform(multipart("/api/brands/" + brand.getBrandId() + "/reports/csv")
                        .file(file)
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Dataset validation failed"))
                .andExpect(jsonPath("$.errors[0].code").value("MISSING_COLUMN"))
                .andExpect(jsonPath("$.errors[0].field").value("text"));

        assertThat(reportRepository.count()).isZero();
        assertThat(postRepository.count()).isZero();
    }

    @Test
    void shouldReturn400_whenRowHasEmptyText() throws Exception {
        String csv = """
                text,url,language
                "",https://example.com/1,EN
                "valid post",https://example.com/2,EN
                """;
        MockMultipartFile file = new MockMultipartFile(
                "file", "posts.csv", "text/csv", csv.getBytes());

        mockMvc.perform(multipart("/api/brands/" + brand.getBrandId() + "/reports/csv")
                        .file(file)
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[?(@.code == 'EMPTY_ROW')]").exists());

        assertThat(reportRepository.count()).isZero();
        assertThat(postRepository.count()).isZero();
    }

    @Test
    void shouldReturn400_whenLanguageIsInvalid() throws Exception {
        String csv = """
                text,language
                "valid text",FR
                """;
        MockMultipartFile file = new MockMultipartFile(
                "file", "posts.csv", "text/csv", csv.getBytes());

        mockMvc.perform(multipart("/api/brands/" + brand.getBrandId() + "/reports/csv")
                        .file(file)
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].code").value("INVALID_LANGUAGE"))
                .andExpect(jsonPath("$.errors[0].field").value("language"));
    }

    @Test
    void shouldReturn400_whenFileHasNoDataRows() throws Exception {
        String csv = "text,url,language\n";
        MockMultipartFile file = new MockMultipartFile(
                "file", "posts.csv", "text/csv", csv.getBytes());

        mockMvc.perform(multipart("/api/brands/" + brand.getBrandId() + "/reports/csv")
                        .file(file)
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].code").value("NO_DATA_ROWS"));
    }

    @Test
    void shouldReturn400_whenFileIsNotCsv() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "posts.txt", "text/plain", "hello".getBytes());

        mockMvc.perform(multipart("/api/brands/" + brand.getBrandId() + "/reports/csv")
                        .file(file)
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].code").value("UNSUPPORTED_FORMAT"));
    }

    @Test
    void shouldReturn400_whenFileIsEmpty() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "posts.csv", "text/csv", new byte[0]);

        mockMvc.perform(multipart("/api/brands/" + brand.getBrandId() + "/reports/csv")
                        .file(file)
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].code").value("EMPTY_FILE"));
    }

    @Test
    void shouldReturn404_whenBrandDoesNotExist() throws Exception {
        String csv = "text\n\"valid\"\n";
        MockMultipartFile file = new MockMultipartFile(
                "file", "posts.csv", "text/csv", csv.getBytes());

        mockMvc.perform(multipart("/api/brands/999999/reports/csv")
                        .file(file)
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldReturn400_whenBrandBelongsToDifferentCompany() throws Exception {
        Brand otherBrand = Brand.builder()
                .brandName("Rival")
                .company(otherCompany)
                .build();
        otherBrand = brandRepository.save(otherBrand);

        String csv = "text\n\"valid\"\n";
        MockMultipartFile file = new MockMultipartFile(
                "file", "posts.csv", "text/csv", csv.getBytes());

        mockMvc.perform(multipart("/api/brands/" + otherBrand.getBrandId() + "/reports/csv")
                        .file(file)
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value(org.hamcrest.Matchers.containsString("does not belong")));
    }

    @Test
    void shouldReturn401_whenUnauthenticated() throws Exception {
        String csv = "text\n\"valid\"\n";
        MockMultipartFile file = new MockMultipartFile(
                "file", "posts.csv", "text/csv", csv.getBytes());

        mockMvc.perform(multipart("/api/brands/" + brand.getBrandId() + "/reports/csv")
                        .file(file))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldReturnAllErrors_whenMultipleRowIssuesPresent() throws Exception {
        String csv = """
                text,language
                "",EN
                "",EN
                "valid post",FR
                """;
        MockMultipartFile file = new MockMultipartFile(
                "file", "posts.csv", "text/csv", csv.getBytes());

        mockMvc.perform(multipart("/api/brands/" + brand.getBrandId() + "/reports/csv")
                        .file(file)
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors").isArray())
                .andExpect(jsonPath("$.errors.length()")
                        .value(org.hamcrest.Matchers.greaterThanOrEqualTo(3)));
    }

    @SuppressWarnings("unused")
    private static MediaType csvMediaType() {
        return MediaType.parseMediaType("text/csv");
    }
}
