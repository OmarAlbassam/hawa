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
import com.hawa.hawa_backend.enums.LanguageEnum;
import com.hawa.hawa_backend.enums.ReportStatusEnum;
import com.hawa.hawa_backend.enums.UserRoleEnum;
import com.hawa.hawa_backend.post.Post;
import com.hawa.hawa_backend.post.PostRepository;
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
class ViewAdminAnalyticsTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private CompanyRepository companyRepository;
    @Autowired private BrandRepository brandRepository;
    @Autowired private ReportRepository reportRepository;
    @Autowired private PostRepository postRepository;
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
    void shouldReturnAnalytics_whenAdminRequestsWithData() throws Exception {
        createUser("user1@example.com", UserRoleEnum.MARKETING_USER, companyId);

        Company otherCompany = new Company();
        otherCompany.setCompanyName("Other Corp");
        companyRepository.save(otherCompany);

        Brand brand = Brand.builder()
                .brandName("Test Brand")
                .company(companyRepository.findById(companyId).orElseThrow())
                .build();
        brandRepository.save(brand);

        Report completedReport = Report.builder()
                .user(adminUser)
                .brand(brand)
                .dataSource(DataSourceEnum.REDDIT)
                .status(ReportStatusEnum.COMPLETED)
                .build();
        reportRepository.save(completedReport);

        Report pendingReport = Report.builder()
                .user(adminUser)
                .brand(brand)
                .dataSource(DataSourceEnum.CSV_UPLOAD)
                .status(ReportStatusEnum.PENDING)
                .build();
        reportRepository.save(pendingReport);

        Post post1 = Post.builder()
                .report(completedReport)
                .postText("Great product!")
                .language(LanguageEnum.EN)
                .build();
        postRepository.save(post1);

        mockMvc.perform(get("/api/admin/analytics")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalUsers").value(2))
                .andExpect(jsonPath("$.totalCompanies").value(2))
                .andExpect(jsonPath("$.totalReports").value(2))
                .andExpect(jsonPath("$.reportsByStatus.COMPLETED").value(1))
                .andExpect(jsonPath("$.reportsByStatus.PENDING").value(1))
                .andExpect(jsonPath("$.reportsByStatus.PROCESSING").value(0))
                .andExpect(jsonPath("$.reportsByStatus.FAILED").value(0))
                .andExpect(jsonPath("$.totalPostsAnalyzed").value(1));
    }

    @Test
    void shouldReject403_whenNonAdminAccessesAnalytics() throws Exception {
        User marketingUser = createUser("marketing@example.com",
                UserRoleEnum.MARKETING_USER, companyId);
        String marketingToken = jwtService.generateAccessToken(marketingUser);

        mockMvc.perform(get("/api/admin/analytics")
                        .header("Authorization", "Bearer " + marketingToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldReturnZeroCounts_whenNoData() throws Exception {
        mockMvc.perform(get("/api/admin/analytics")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalUsers").value(1))
                .andExpect(jsonPath("$.totalCompanies").value(1))
                .andExpect(jsonPath("$.totalReports").value(0))
                .andExpect(jsonPath("$.totalPostsAnalyzed").value(0));
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
