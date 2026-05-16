package com.hawa.hawa_backend.features;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hawa.hawa_backend.TestcontainersConfiguration;
import com.hawa.hawa_backend.auth.JwtService;
import com.hawa.hawa_backend.company.Company;
import com.hawa.hawa_backend.company.CompanyRepository;
import com.hawa.hawa_backend.enums.UserRoleEnum;
import com.hawa.hawa_backend.user.User;
import com.hawa.hawa_backend.user.UserRepository;

import org.junit.jupiter.api.BeforeEach;
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
class ManageCompaniesTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private CompanyRepository companyRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JwtService jwtService;
    @Autowired private JdbcTemplate jdbcTemplate;

    private Long companyId;
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

        User adminUser = User.builder()
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
    void shouldCreateCompany_whenAdminRequests() throws Exception {
        mockMvc.perform(post("/api/admin/companies")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"companyName": "New Corp"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.companyId").isNumber())
                .andExpect(jsonPath("$.companyName").value("New Corp"))
                .andExpect(jsonPath("$.createdAt").isNotEmpty());
    }

    @Test
    void shouldListCompaniesPaginated_whenAdminRequests() throws Exception {
        mockMvc.perform(get("/api/admin/companies")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].companyName").value("Test Corp"));
    }

    @Test
    void shouldGetCompany_whenCompanyExists() throws Exception {
        mockMvc.perform(get("/api/admin/companies/" + companyId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.companyName").value("Test Corp"));
    }

    @Test
    void shouldUpdateCompany_whenCompanyExists() throws Exception {
        mockMvc.perform(put("/api/admin/companies/" + companyId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"companyName": "Renamed Corp"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.companyName").value("Renamed Corp"));
    }

    @Test
    void shouldDeleteCompany_whenCompanyExists() throws Exception {
        Company toDelete = new Company();
        toDelete.setCompanyName("Delete Me");
        toDelete = companyRepository.save(toDelete);

        mockMvc.perform(delete("/api/admin/companies/" + toDelete.getCompanyId())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNoContent());
    }

    @Test
    void shouldReject404_whenGettingNonExistentCompany() throws Exception {
        mockMvc.perform(get("/api/admin/companies/99999")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound());
    }
}
