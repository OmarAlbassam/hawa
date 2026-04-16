package com.hawa.hawa_backend.report;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hawa.hawa_backend.TestcontainersConfiguration;
import com.hawa.hawa_backend.auth.JwtService;
import com.hawa.hawa_backend.brand.Brand;
import com.hawa.hawa_backend.brand.BrandRepository;
import com.hawa.hawa_backend.company.Company;
import com.hawa.hawa_backend.company.CompanyRepository;
import com.hawa.hawa_backend.enums.DataSourceEnum;
import com.hawa.hawa_backend.enums.ReportStatusEnum;
import com.hawa.hawa_backend.enums.UserRoleEnum;
import com.hawa.hawa_backend.user.User;
import com.hawa.hawa_backend.user.UserRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
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
class ReportControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private CompanyRepository companyRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private BrandRepository brandRepository;
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
    }

    @Nested
    class ListReports {

        @Test
        void shouldReturnPaginatedReports() throws Exception {
            createReport(brand, ReportStatusEnum.COMPLETED);
            createReport(brand, ReportStatusEnum.PENDING);

            mockMvc.perform(get("/api/reports")
                            .header("Authorization", "Bearer " + userToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content").isArray())
                    .andExpect(jsonPath("$.content.length()").value(2))
                    .andExpect(jsonPath("$.content[0].reportId").isNumber())
                    .andExpect(jsonPath("$.content[0].brandName").value("Nike"))
                    .andExpect(jsonPath("$.totalElements").value(2));
        }

        @Test
        void shouldFilterByBrandId() throws Exception {
            Brand otherBrand = Brand.builder()
                    .brandName("Adidas")
                    .company(company)
                    .build();
            otherBrand = brandRepository.save(otherBrand);

            createReport(brand, ReportStatusEnum.COMPLETED);
            createReport(otherBrand, ReportStatusEnum.COMPLETED);

            mockMvc.perform(get("/api/reports")
                            .param("brandId", brand.getBrandId().toString())
                            .header("Authorization", "Bearer " + userToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content.length()").value(1))
                    .andExpect(jsonPath("$.content[0].brandName").value("Nike"));
        }

        @Test
        void shouldFilterByStatus() throws Exception {
            createReport(brand, ReportStatusEnum.COMPLETED);
            createReport(brand, ReportStatusEnum.PENDING);
            createReport(brand, ReportStatusEnum.FAILED);

            mockMvc.perform(get("/api/reports")
                            .param("status", "COMPLETED")
                            .header("Authorization", "Bearer " + userToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content.length()").value(1))
                    .andExpect(jsonPath("$.content[0].status").value("COMPLETED"));
        }

        @Test
        void shouldNotShowOtherCompanyReports() throws Exception {
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
            reportRepository.save(report);

            mockMvc.perform(get("/api/reports")
                            .header("Authorization", "Bearer " + userToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content").isEmpty());
        }

        @Test
        void shouldSortByCreatedAtDesc() throws Exception {
            createReport(brand, ReportStatusEnum.COMPLETED);
            createReport(brand, ReportStatusEnum.PENDING);

            mockMvc.perform(get("/api/reports")
                            .param("sort", "createdAt,desc")
                            .header("Authorization", "Bearer " + userToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content.length()").value(2));
        }

        @Test
        void shouldIncludeReportsCreatedDuringDateToDay() throws Exception {
            // Regression: dateTo filter should include reports created any time on that day
            createReport(brand, ReportStatusEnum.COMPLETED);

            String today = java.time.LocalDate.now().toString();

            mockMvc.perform(get("/api/reports")
                            .param("dateTo", today)
                            .header("Authorization", "Bearer " + userToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content.length()").value(1));
        }

        @Test
        void shouldReject400_whenSortPropertyIsUnknown() throws Exception {
            mockMvc.perform(get("/api/reports")
                            .param("sort", "malicious_col,desc")
                            .header("Authorization", "Bearer " + userToken))
                    .andExpect(status().isBadRequest());
        }

        @Test
        void shouldReturnEmptyPage_whenNoReports() throws Exception {
            mockMvc.perform(get("/api/reports")
                            .header("Authorization", "Bearer " + userToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content").isEmpty())
                    .andExpect(jsonPath("$.totalElements").value(0));
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

    private Report createReport(Brand targetBrand, ReportStatusEnum status) {
        Report report = Report.builder()
                .brand(targetBrand)
                .user(marketingUser)
                .dataSource(DataSourceEnum.REDDIT)
                .status(status)
                .build();
        return reportRepository.save(report);
    }
}
