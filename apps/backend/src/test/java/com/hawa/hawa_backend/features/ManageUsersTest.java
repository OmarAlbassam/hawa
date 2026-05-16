package com.hawa.hawa_backend.features;

import static org.assertj.core.api.Assertions.assertThat;
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
class ManageUsersTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private CompanyRepository companyRepository;
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

        adminUser = createUserDirectly("admin@example.com", "Password1", UserRoleEnum.ADMIN, null);
        adminToken = jwtService.generateAccessToken(adminUser);
    }

    @Nested
    class WhenAdminManagesUsers {

        @Test
        void shouldCreateUser_whenAdminRequests() throws Exception {
            mockMvc.perform(post("/api/admin/users")
                            .header("Authorization", "Bearer " + adminToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(createUserJson("jane@example.com", "Password1",
                                    companyId, "MARKETING_USER")))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.userId").isNumber())
                    .andExpect(jsonPath("$.firstName").value("Jane"))
                    .andExpect(jsonPath("$.lastName").value("Doe"))
                    .andExpect(jsonPath("$.email").value("jane@example.com"))
                    .andExpect(jsonPath("$.role").value("MARKETING_USER"))
                    .andExpect(jsonPath("$.company.companyId").value(companyId))
                    .andExpect(jsonPath("$.company.companyName").value("Test Corp"))
                    .andExpect(jsonPath("$.createdAt").isNotEmpty())
                    .andExpect(jsonPath("$.updatedAt").isNotEmpty());
        }

        @Test
        void shouldReject403_whenNonAdminCreatesUser() throws Exception {
            User marketingUser = createUserDirectly("marketing@example.com", "Password1",
                    UserRoleEnum.MARKETING_USER, companyId);
            String marketingToken = jwtService.generateAccessToken(marketingUser);

            mockMvc.perform(post("/api/admin/users")
                            .header("Authorization", "Bearer " + marketingToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(createUserJson("new@example.com", "Password1",
                                    companyId, "MARKETING_USER")))
                    .andExpect(status().isForbidden());
        }

        @Test
        void shouldReject401_whenUnauthenticatedCreatesUser() throws Exception {
            mockMvc.perform(post("/api/admin/users")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(createUserJson("new@example.com", "Password1",
                                    companyId, "MARKETING_USER")))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        void shouldReject409_whenCreatingUserWithDuplicateEmail() throws Exception {
            createUserDirectly("dup@example.com", "Password1",
                    UserRoleEnum.MARKETING_USER, companyId);

            mockMvc.perform(post("/api/admin/users")
                            .header("Authorization", "Bearer " + adminToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(createUserJson("dup@example.com", "Password1",
                                    companyId, "MARKETING_USER")))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.message").value("Email already registered"));
        }

        @Test
        void shouldReject404_whenCreatingUserWithInvalidCompanyId() throws Exception {
            mockMvc.perform(post("/api/admin/users")
                            .header("Authorization", "Bearer " + adminToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(createUserJson("new@example.com", "Password1",
                                    99999L, "MARKETING_USER")))
                    .andExpect(status().isNotFound());
        }

        @Test
        void shouldReject400_whenCreatingUserWithWeakPassword() throws Exception {
            mockMvc.perform(post("/api/admin/users")
                            .header("Authorization", "Bearer " + adminToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(createUserJson("new@example.com", "weak",
                                    companyId, "MARKETING_USER")))
                    .andExpect(status().isBadRequest());
        }

        @Test
        void shouldCreateAdmin_whenCompanyIsNull() throws Exception {
            mockMvc.perform(post("/api/admin/users")
                            .header("Authorization", "Bearer " + adminToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(createUserJson("newadmin@example.com", "Password1",
                                    null, "ADMIN")))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.role").value("ADMIN"))
                    .andExpect(jsonPath("$.company").isEmpty());
        }

        @Test
        void shouldReject400_whenCreatingAdminWithCompanyId() throws Exception {
            mockMvc.perform(post("/api/admin/users")
                            .header("Authorization", "Bearer " + adminToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(createUserJson("badadmin@example.com", "Password1",
                                    companyId, "ADMIN")))
                    .andExpect(status().isBadRequest());
        }

        @Test
        void shouldReject400_whenCreatingMarketingUserWithoutCompany() throws Exception {
            mockMvc.perform(post("/api/admin/users")
                            .header("Authorization", "Bearer " + adminToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(createUserJson("nocompany@example.com", "Password1",
                                    null, "MARKETING_USER")))
                    .andExpect(status().isBadRequest());
        }

        @Test
        void shouldGetSingleUser_whenAdminRequests() throws Exception {
            User user = createUserDirectly("target@example.com", "Password1",
                    UserRoleEnum.MARKETING_USER, companyId);

            mockMvc.perform(get("/api/admin/users/" + user.getUserId())
                            .header("Authorization", "Bearer " + adminToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.userId").value(user.getUserId()))
                    .andExpect(jsonPath("$.email").value("target@example.com"))
                    .andExpect(jsonPath("$.company.companyName").value("Test Corp"));
        }

        @Test
        void shouldReject404_whenGettingNonExistentUser() throws Exception {
            mockMvc.perform(get("/api/admin/users/99999")
                            .header("Authorization", "Bearer " + adminToken))
                    .andExpect(status().isNotFound());
        }

        @Test
        void shouldListUsersPaginated_whenAdminRequests() throws Exception {
            createUserDirectly("user1@example.com", "Password1",
                    UserRoleEnum.MARKETING_USER, companyId);
            createUserDirectly("user2@example.com", "Password1",
                    UserRoleEnum.MARKETING_USER, companyId);

            mockMvc.perform(get("/api/admin/users")
                            .param("page", "0")
                            .param("size", "10")
                            .header("Authorization", "Bearer " + adminToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalElements").value(3))
                    .andExpect(jsonPath("$.content").isArray())
                    .andExpect(jsonPath("$.content.length()").value(3));
        }

        @Test
        void shouldFilterUsersByRole_whenRoleParamProvided() throws Exception {
            createUserDirectly("marketing1@example.com", "Password1",
                    UserRoleEnum.MARKETING_USER, companyId);

            mockMvc.perform(get("/api/admin/users")
                            .param("role", "MARKETING_USER")
                            .header("Authorization", "Bearer " + adminToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalElements").value(1))
                    .andExpect(jsonPath("$.content[0].role").value("MARKETING_USER"));
        }

        @Test
        void shouldFilterUsersByCompany_whenCompanyIdProvided() throws Exception {
            Company otherCompany = new Company();
            otherCompany.setCompanyName("Other Corp");
            Long otherCompanyId = companyRepository.save(otherCompany).getCompanyId();

            createUserDirectly("other@example.com", "Password1",
                    UserRoleEnum.MARKETING_USER, otherCompanyId);

            mockMvc.perform(get("/api/admin/users")
                            .param("companyId", otherCompanyId.toString())
                            .header("Authorization", "Bearer " + adminToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalElements").value(1))
                    .andExpect(jsonPath("$.content[0].email").value("other@example.com"));
        }

        @Test
        void shouldSearchUsersByEmail_whenSearchParamProvided() throws Exception {
            createUserDirectly("alice@example.com", "Password1",
                    UserRoleEnum.MARKETING_USER, companyId);

            mockMvc.perform(get("/api/admin/users")
                            .param("search", "alice")
                            .header("Authorization", "Bearer " + adminToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalElements").value(1))
                    .andExpect(jsonPath("$.content[0].email").value("alice@example.com"));
        }

        @Test
        void shouldUpdateUser_whenAdminRequests() throws Exception {
            User user = createUserDirectly("update@example.com", "Password1",
                    UserRoleEnum.MARKETING_USER, companyId);

            mockMvc.perform(put("/api/admin/users/" + user.getUserId())
                            .header("Authorization", "Bearer " + adminToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                        "firstName": "Updated",
                                        "lastName": "Name",
                                        "email": "updated@example.com"
                                    }
                                    """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.firstName").value("Updated"))
                    .andExpect(jsonPath("$.lastName").value("Name"))
                    .andExpect(jsonPath("$.email").value("updated@example.com"))
                    .andExpect(jsonPath("$.role").value("MARKETING_USER"));
        }

        @Test
        void shouldUpdateUserRole_whenAdminRequests() throws Exception {
            User user = createUserDirectly("role@example.com", "Password1",
                    UserRoleEnum.MARKETING_USER, companyId);

            mockMvc.perform(put("/api/admin/users/" + user.getUserId())
                            .header("Authorization", "Bearer " + adminToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                        "firstName": "Jane",
                                        "lastName": "Doe",
                                        "email": "role@example.com",
                                        "role": "ADMIN"
                                    }
                                    """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.role").value("ADMIN"));
        }

        @Test
        void shouldReject409_whenUpdatingToExistingEmail() throws Exception {
            createUserDirectly("existing@example.com", "Password1",
                    UserRoleEnum.MARKETING_USER, companyId);
            User user = createUserDirectly("change@example.com", "Password1",
                    UserRoleEnum.MARKETING_USER, companyId);

            mockMvc.perform(put("/api/admin/users/" + user.getUserId())
                            .header("Authorization", "Bearer " + adminToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                        "firstName": "Jane",
                                        "lastName": "Doe",
                                        "email": "existing@example.com"
                                    }
                                    """))
                    .andExpect(status().isConflict());
        }

        @Test
        void shouldDeleteUser_whenAdminRequests() throws Exception {
            User user = createUserDirectly("delete@example.com", "Password1",
                    UserRoleEnum.MARKETING_USER, companyId);

            mockMvc.perform(delete("/api/admin/users/" + user.getUserId())
                            .header("Authorization", "Bearer " + adminToken))
                    .andExpect(status().isNoContent());

            assertThat(userRepository.findById(user.getUserId())).isEmpty();
        }

        @Test
        void shouldReject400_whenAdminDeletesSelf() throws Exception {
            mockMvc.perform(delete("/api/admin/users/" + adminUser.getUserId())
                            .header("Authorization", "Bearer " + adminToken))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("Cannot delete your own account"));
        }

        @Test
        void shouldReject404_whenDeletingNonExistentUser() throws Exception {
            mockMvc.perform(delete("/api/admin/users/99999")
                            .header("Authorization", "Bearer " + adminToken))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    class WhenCallingRegisterEndpoint {

        @Test
        void shouldReject403_whenNonAdminCallsRegister() throws Exception {
            User marketingUser = createUserDirectly("marketing@example.com", "Password1",
                    UserRoleEnum.MARKETING_USER, companyId);
            String marketingToken = jwtService.generateAccessToken(marketingUser);

            mockMvc.perform(post("/api/auth/register")
                            .header("Authorization", "Bearer " + marketingToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                        "firstName": "New",
                                        "lastName": "User",
                                        "email": "new@example.com",
                                        "password": "Password1",
                                        "companyId": %d,
                                        "role": "MARKETING_USER"
                                    }
                                    """.formatted(companyId)))
                    .andExpect(status().isForbidden());
        }

        @Test
        void shouldReject401_whenUnauthenticatedCallsRegister() throws Exception {
            mockMvc.perform(post("/api/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                        "firstName": "New",
                                        "lastName": "User",
                                        "email": "new@example.com",
                                        "password": "Password1",
                                        "companyId": %d,
                                        "role": "MARKETING_USER"
                                    }
                                    """.formatted(companyId)))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        void shouldAllowRegister_whenAdminCalls() throws Exception {
            mockMvc.perform(post("/api/auth/register")
                            .header("Authorization", "Bearer " + adminToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                        "firstName": "New",
                                        "lastName": "User",
                                        "email": "new@example.com",
                                        "password": "Password1",
                                        "companyId": %d,
                                        "role": "MARKETING_USER"
                                    }
                                    """.formatted(companyId)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.userId").isNumber());
        }
    }

    private User createUserDirectly(String email, String password,
                                     UserRoleEnum role, Long targetCompanyId) {
        Company company = targetCompanyId == null ? null
                : companyRepository.findById(targetCompanyId).orElseThrow();
        User user = User.builder()
                .firstName("Jane")
                .lastName("Doe")
                .email(email)
                .password(passwordEncoder.encode(password))
                .company(company)
                .role(role)
                .build();
        return userRepository.save(user);
    }

    private String createUserJson(String email, String password,
                                   Long targetCompanyId, String role) {
        String companyIdLiteral = targetCompanyId == null ? "null" : targetCompanyId.toString();
        return """
                {
                    "firstName": "Jane",
                    "lastName": "Doe",
                    "email": "%s",
                    "password": "%s",
                    "companyId": %s,
                    "role": "%s"
                }
                """.formatted(email, password, companyIdLiteral, role);
    }
}
