package com.hawa.hawa_backend.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hawa.hawa_backend.company.Company;
import com.hawa.hawa_backend.company.CompanyRepository;
import com.hawa.hawa_backend.enums.UserRoleEnum;
import com.hawa.hawa_backend.user.User;
import com.hawa.hawa_backend.user.UserRepository;

import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.hawa.hawa_backend.TestcontainersConfiguration;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class AuthFlowTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private CompanyRepository companyRepository;
    @Autowired private RefreshTokenRepository refreshTokenRepository;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JwtService jwtService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private Long companyId;
    private String adminToken;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("DELETE FROM feedback");
        jdbcTemplate.execute("DELETE FROM review");
        jdbcTemplate.execute("DELETE FROM post");
        jdbcTemplate.execute("DELETE FROM report");
        jdbcTemplate.execute("DELETE FROM keyword");
        jdbcTemplate.execute("DELETE FROM brand");
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();
        companyRepository.deleteAll();

        Company company = new Company();
        company.setCompanyName("Test Corp");
        companyId = companyRepository.save(company).getCompanyId();

        // Create an admin user for registration tests (register now requires ADMIN role).
        // Admins are platform-level and do not belong to a tenant company.
        User admin = User.builder()
                .firstName("Admin")
                .lastName("User")
                .email("testadmin@example.com")
                .password(passwordEncoder.encode("Password1"))
                .company(null)
                .role(UserRoleEnum.ADMIN)
                .build();
        admin = userRepository.save(admin);
        adminToken = jwtService.generateAccessToken(admin);
    }

    // ---- 1. Registration (requires ADMIN role) ----

    @Test
    void shouldRegisterNewUser_andReturnUserInfo() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson("john@example.com", "Password1", "MARKETING_USER")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("john@example.com"))
                .andExpect(jsonPath("$.firstName").value("John"))
                .andExpect(jsonPath("$.lastName").value("Doe"))
                .andExpect(jsonPath("$.role").value("MARKETING_USER"))
                .andExpect(jsonPath("$.userId").isNumber());
    }

    @Test
    void shouldReject409_whenRegisteringDuplicateEmail() throws Exception {
        registerUser("dup@example.com", "Password1", "MARKETING_USER");

        mockMvc.perform(post("/api/auth/register")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson("dup@example.com", "Password1", "MARKETING_USER")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Email already registered"));
    }

    @Test
    void shouldReject400_whenPasswordTooWeak() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson("weak@example.com", "weak", "MARKETING_USER")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldReject400_whenPasswordMissingUppercase() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson("weak@example.com", "password1", "MARKETING_USER")))
                .andExpect(status().isBadRequest());
    }

    // ---- 2. Login ----

    @Test
    void shouldLogin_andReturnTokensAndUserInfo() throws Exception {
        registerUser("login@example.com", "Password1", "ADMIN");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "login@example.com", "password": "Password1"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.user.email").value("login@example.com"))
                .andExpect(jsonPath("$.user.role").value("ADMIN"));
    }

    @Test
    void shouldCleanUpOldRefreshTokens_onLogin() throws Exception {
        // Register creates a user (no tokens returned), then login to get first refresh token
        registerUser("cleanup@example.com", "Password1", "MARKETING_USER");

        MvcResult firstLoginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "cleanup@example.com", "password": "Password1"}
                                """))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode firstLoginTokens = objectMapper.readTree(firstLoginResult.getResponse().getContentAsString());
        String firstRefreshToken = firstLoginTokens.get("refreshToken").asText();

        // Second login should revoke the old refresh token and issue a new one
        MvcResult secondLoginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "cleanup@example.com", "password": "Password1"}
                                """))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode secondLoginTokens = objectMapper.readTree(secondLoginResult.getResponse().getContentAsString());
        String secondRefreshToken = secondLoginTokens.get("refreshToken").asText();

        // Old refresh token from first login should be gone
        assertThat(refreshTokenRepository.findByToken(firstRefreshToken).isPresent()).isFalse();
        // New refresh token from second login should exist
        assertThat(refreshTokenRepository.findByToken(secondRefreshToken).isPresent()).isTrue();
    }

    @Test
    void shouldReject401_whenLoginWithWrongPassword() throws Exception {
        registerUser("wrongpw@example.com", "Password1", "MARKETING_USER");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "wrongpw@example.com", "password": "WrongPass1"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid email or password"));
    }

    // ---- 3. Refresh token: JWT expired, use refresh token to get new one ----

    @Test
    void shouldIssueNewTokens_whenRefreshingWithValidRefreshToken() throws Exception {
        registerUser("refresh@example.com", "Password1", "MARKETING_USER");
        JsonNode tokens = loginUser("refresh@example.com", "Password1");
        String refreshToken = tokens.get("refreshToken").asText();

        MvcResult result = mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\": \"" + refreshToken + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.user.email").value("refresh@example.com"))
                .andReturn();

        JsonNode newTokens = objectMapper.readTree(result.getResponse().getContentAsString());
        String newRefreshToken = newTokens.get("refreshToken").asText();
        String newAccessToken = newTokens.get("accessToken").asText();

        // Old refresh token should be rotated (deleted), new one should exist
        assertThat(refreshTokenRepository.findByToken(refreshToken)).isEmpty();
        assertThat(refreshTokenRepository.findByToken(newRefreshToken)).isPresent();

        // New refresh token should be different from old one
        assertThat(newRefreshToken).isNotEqualTo(refreshToken);

        // New access token should be valid (non-empty)
        assertThat(newAccessToken).isNotBlank();
    }

    // ---- 4. Refresh token: JWT expired and refresh token is wrong ----

    @Test
    void shouldReject401_whenRefreshingWithInvalidRefreshToken() throws Exception {
        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"refreshToken": "non-existent-uuid"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid or expired refresh token"));
    }

    @Test
    void shouldReject401_whenRefreshTokenAlreadyRotated() throws Exception {
        registerUser("rotated@example.com", "Password1", "MARKETING_USER");
        JsonNode tokens = loginUser("rotated@example.com", "Password1");
        String refreshToken = tokens.get("refreshToken").asText();

        // Use the refresh token once (it gets rotated)
        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\": \"" + refreshToken + "\"}"))
                .andExpect(status().isOk());

        // Try to use the same refresh token again — should fail (replay protection)
        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\": \"" + refreshToken + "\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldReject401_whenRefreshTokenIsExpired() throws Exception {
        registerUser("expired@example.com", "Password1", "MARKETING_USER");
        JsonNode tokens = loginUser("expired@example.com", "Password1");
        String refreshToken = tokens.get("refreshToken").asText();

        // Manually expire the refresh token in the DB
        jdbcTemplate.update(
                "UPDATE refresh_token SET expires_at = ? WHERE token = ?",
                LocalDateTime.now().minusDays(1), refreshToken);

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\": \"" + refreshToken + "\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid or expired refresh token"));

        // Expired token should be cleaned up from DB (not rolled back)
        assertThat(refreshTokenRepository.findByToken(refreshToken).isPresent()).isFalse();
    }

    // ---- 5. Invalid/missing JWT on protected endpoint ----

    @Test
    void shouldReject401_whenAccessingProtectedEndpointWithoutToken() throws Exception {
        mockMvc.perform(get("/api/brands"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldReject401_whenAccessingProtectedEndpointWithGarbageToken() throws Exception {
        mockMvc.perform(get("/api/brands")
                        .header("Authorization", "Bearer not-a-real-jwt"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldAccessProtectedEndpoint_withValidToken() throws Exception {
        registerUser("valid@example.com", "Password1", "MARKETING_USER");
        JsonNode tokens = loginUser("valid@example.com", "Password1");
        String accessToken = tokens.get("accessToken").asText();

        // Should not get 401 — the endpoint may 404 but it passes auth
        mockMvc.perform(get("/api/brands")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(result -> assertThat(result.getResponse().getStatus()).isNotEqualTo(401));
    }

    // ---- 6. Role-related ----

    @Test
    void shouldReturnCorrectRole_whenRegisteringAsAdmin() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson("newadmin@example.com", "Password1", "ADMIN")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("ADMIN"));
    }

    @Test
    void shouldReturnCorrectRole_whenRegisteringAsMarketingUser() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson("marketing@example.com", "Password1", "MARKETING_USER")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("MARKETING_USER"));
    }

    @Test
    void shouldPreserveRoleThroughLoginFlow() throws Exception {
        registerUser("rolecheck@example.com", "Password1", "ADMIN");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "rolecheck@example.com", "password": "Password1"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.role").value("ADMIN"));
    }

    // ---- 7. Logout ----

    @Test
    void shouldInvalidateRefreshToken_onLogout() throws Exception {
        registerUser("logout@example.com", "Password1", "MARKETING_USER");
        JsonNode tokens = loginUser("logout@example.com", "Password1");
        String refreshToken = tokens.get("refreshToken").asText();

        mockMvc.perform(post("/api/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\": \"" + refreshToken + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Logged out successfully"));

        // Refresh token should no longer work
        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\": \"" + refreshToken + "\"}"))
                .andExpect(status().isUnauthorized());
    }

    // ---- 8. Spring MVC exception handling (regression: malformed requests should not return 500) ----

    @Test
    void shouldReturn400_whenRequestBodyIsMalformedJson() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ not valid json }"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldReturn415_whenContentTypeIsNotJson() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("not json"))
                .andExpect(status().isUnsupportedMediaType());
    }

    // ---- Helpers ----

    private JsonNode loginUser(String email, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "%s", "password": "%s"}
                                """.formatted(email, password)))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private String registerJson(String email, String password, String role) {
        String companyIdLiteral = "ADMIN".equals(role) ? "null" : companyId.toString();
        return """
                {
                    "firstName": "John",
                    "lastName": "Doe",
                    "email": "%s",
                    "password": "%s",
                    "companyId": %s,
                    "role": "%s"
                }
                """.formatted(email, password, companyIdLiteral, role);
    }

    private JsonNode registerUser(String email, String password, String role) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson(email, password, role)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }
}
