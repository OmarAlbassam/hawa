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
import com.hawa.hawa_backend.enums.DataSourceEnum;
import com.hawa.hawa_backend.enums.ReportStatusEnum;
import com.hawa.hawa_backend.enums.UserRoleEnum;
import com.hawa.hawa_backend.report.Report;
import com.hawa.hawa_backend.report.ReportRepository;
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
class ViewDashboardTest {

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
    void shouldReturnDashboardWithBrandsAndStats_whenCompanyHasReports() throws Exception {
        Brand brand = createBrand("Nike", company);
        createReport(brand, marketingUser, ReportStatusEnum.COMPLETED);
        createReport(brand, marketingUser, ReportStatusEnum.PENDING);

        mockMvc.perform(get("/api/dashboard")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.brands").isArray())
                .andExpect(jsonPath("$.brands[0].brandId").value(brand.getBrandId()))
                .andExpect(jsonPath("$.brands[0].brandName").value("Nike"))
                .andExpect(jsonPath("$.recentReports").isArray())
                .andExpect(jsonPath("$.recentReports.length()").value(2))
                .andExpect(jsonPath("$.stats.totalReports").value(2))
                .andExpect(jsonPath("$.stats.totalBrands").value(1))
                .andExpect(jsonPath("$.stats.totalPostsAnalyzed").value(0));
    }

    @Test
    void shouldReturnEmptyDashboard_whenCompanyHasNoBrands() throws Exception {
        mockMvc.perform(get("/api/dashboard")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.brands").isEmpty())
                .andExpect(jsonPath("$.recentReports").isEmpty())
                .andExpect(jsonPath("$.stats.totalReports").value(0))
                .andExpect(jsonPath("$.stats.totalBrands").value(0));
    }

    @Test
    void shouldRespectRecentReportsLimit_whenLimitParamProvided() throws Exception {
        Brand brand = createBrand("Nike", company);
        for (int i = 0; i < 10; i++) {
            createReport(brand, marketingUser, ReportStatusEnum.COMPLETED);
        }

        mockMvc.perform(get("/api/dashboard")
                        .param("recentReportsLimit", "3")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recentReports.length()").value(3))
                .andExpect(jsonPath("$.stats.totalReports").value(10));
    }

    @Test
    void shouldNotShowOtherCompanyData_whenScopedByCompany() throws Exception {
        Company otherCompany = new Company();
        otherCompany.setCompanyName("Other Corp");
        otherCompany = companyRepository.save(otherCompany);

        Brand otherBrand = createBrand("Adidas", otherCompany);
        User otherUser = createUser("other@example.com", UserRoleEnum.MARKETING_USER, otherCompany);
        createReport(otherBrand, otherUser, ReportStatusEnum.COMPLETED);

        mockMvc.perform(get("/api/dashboard")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.brands").isEmpty())
                .andExpect(jsonPath("$.recentReports").isEmpty())
                .andExpect(jsonPath("$.stats.totalReports").value(0));
    }

    @Test
    void shouldReject400_whenRecentReportsLimitIsZero() throws Exception {
        mockMvc.perform(get("/api/dashboard")
                        .param("recentReportsLimit", "0")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldReject400_whenRecentReportsLimitIsNegative() throws Exception {
        mockMvc.perform(get("/api/dashboard")
                        .param("recentReportsLimit", "-1")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldReject401_whenUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/dashboard"))
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

    private Report createReport(Brand brand, User user, ReportStatusEnum status) {
        Report report = Report.builder()
                .brand(brand)
                .user(user)
                .dataSource(DataSourceEnum.REDDIT)
                .status(status)
                .build();
        return reportRepository.save(report);
    }
}
