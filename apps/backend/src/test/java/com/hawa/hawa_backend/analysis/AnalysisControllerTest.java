package com.hawa.hawa_backend.analysis;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hawa.hawa_backend.TestcontainersConfiguration;
import com.hawa.hawa_backend.auth.JwtService;
import com.hawa.hawa_backend.brand.Brand;
import com.hawa.hawa_backend.brand.BrandRepository;
import com.hawa.hawa_backend.company.Company;
import com.hawa.hawa_backend.company.CompanyRepository;
import com.hawa.hawa_backend.enums.DataSourceEnum;
import com.hawa.hawa_backend.enums.KeywordTypeEnum;
import com.hawa.hawa_backend.enums.ReportStatusEnum;
import com.hawa.hawa_backend.enums.UserRoleEnum;
import com.hawa.hawa_backend.keyword.Keyword;
import com.hawa.hawa_backend.keyword.KeywordRepository;
import com.hawa.hawa_backend.report.Report;
import com.hawa.hawa_backend.report.ReportRepository;
import com.hawa.hawa_backend.user.User;
import com.hawa.hawa_backend.user.UserRepository;

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

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class AnalysisControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private CompanyRepository companyRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private BrandRepository brandRepository;
    @Autowired private KeywordRepository keywordRepository;
    @Autowired private ReportRepository reportRepository;
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

        Keyword seeded = keywordRepository.save(Keyword.builder()
                .brand(brand)
                .keyword("nike")
                .keywordType(KeywordTypeEnum.BRAND_NAME)
                .build());
        brandKeywordId = seeded.getKeywordId();
    }

    private Long brandKeywordId;

    @Nested
    class StartAnalysis {

        @Test
        void shouldCreateReport_withPendingStatus() throws Exception {
            mockMvc.perform(post("/api/brands/" + brand.getBrandId() + "/reports")
                            .header("Authorization", "Bearer " + userToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(String.format(
                                    "{\"dataSource\": \"REDDIT\", \"selectedKeywordIds\": [%d]}", brandKeywordId)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.reportId").isNumber())
                    .andExpect(jsonPath("$.brandName").value("Nike"))
                    .andExpect(jsonPath("$.status").value("PENDING"))
                    .andExpect(jsonPath("$.dataSource").value("REDDIT"))
                    .andExpect(jsonPath("$.createdAt").isNotEmpty());
        }

        @Test
        void shouldCreateReport_withDateRange() throws Exception {
            mockMvc.perform(post("/api/brands/" + brand.getBrandId() + "/reports")
                            .header("Authorization", "Bearer " + userToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(String.format("""
                                    {
                                        "dataSource": "CSV_UPLOAD",
                                        "dateFrom": "2026-01-01",
                                        "dateTo": "2026-03-31",
                                        "selectedKeywordIds": [%d]
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
                                    "{\"dataSource\": \"REDDIT\", \"selectedKeywordIds\": [%d]}", brandKeywordId)))
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
                                    "{\"dataSource\": \"REDDIT\", \"selectedKeywordIds\": [%d]}", brandKeywordId)))
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
        void shouldReject400_whenSelectedKeywordIdsMissing() throws Exception {
            mockMvc.perform(post("/api/brands/" + brand.getBrandId() + "/reports")
                            .header("Authorization", "Bearer " + userToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"dataSource": "REDDIT"}
                                    """))
                    .andExpect(status().isBadRequest());
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
        }
    }

    @Nested
    class GetReportStatus {

        @Test
        void shouldReturnReportStatus() throws Exception {
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
}
