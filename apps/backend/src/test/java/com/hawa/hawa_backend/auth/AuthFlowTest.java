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
import com.hawa.hawa_backend.user.UserRepository;

import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthFlowTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private CompanyRepository companyRepository;
    @Autowired private RefreshTokenRepository refreshTokenRepository;
    @Autowired private JdbcTemplate jdbcTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private Long companyId;

    @BeforeEach
    void setUp() {
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();
        companyRepository.deleteAll();

        Company company = new Company();
        company.setCompanyName("Test Corp");
        companyId = companyRepository.save(company).getCompanyId();
    }

    // ---- 1. Registration ----

    @Test
    void shouldRegisterNewUser_andReturnTokensAndUserInfo() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson("john@example.com", "Password1", "MARKETING_USER")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.user.email").value("john@example.com"))
                .andExpect(jsonPath("$.user.firstName").value("John"))
                .andExpect(jsonPath("$.user.lastName").value("Doe"))
                .andExpect(jsonPath("$.user.role").value("MARKETING_USER"))
                .andExpect(jsonPath("$.user.userId").isNumber())
                .andReturn();

        // Verify refresh token is stored in DB
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        String refreshToken = body.get("refreshToken").asText();
        assertThat(refreshTokenRepository.findByToken(refreshToken)).isPresent();
    }

    @Test
    void shouldReject409_whenRegisteringDuplicateEmail() throws Exception {
        registerUser("dup@example.com", "Password1", "MARKETING_USER");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson("dup@example.com", "Password1", "MARKETING_USER")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Email already registered"));
    }

    @Test
    void shouldReject400_whenPasswordTooWeak() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson("weak@example.com", "weak", "MARKETING_USER")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldReject400_whenPasswordMissingUppercase() throws Exception {
        mockMvc.perform(post("/api/auth/register")
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
        // Register creates a refresh token
        JsonNode registerTokens = registerUser("cleanup@example.com", "Password1", "MARKETING_USER");
        String registerRefreshToken = registerTokens.get("refreshToken").asText();

        // Login should revoke the old refresh token and issue a new one
        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "cleanup@example.com", "password": "Password1"}
                                """))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode loginTokens = objectMapper.readTree(loginResult.getResponse().getContentAsString());
        String loginRefreshToken = loginTokens.get("refreshToken").asText();

        // Old refresh token from register should be gone
        assertThat(refreshTokenRepository.findByToken(registerRefreshToken).isPresent()).isFalse();
        // New refresh token from login should exist
        assertThat(refreshTokenRepository.findByToken(loginRefreshToken).isPresent()).isTrue();
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
        JsonNode tokens = registerUser("refresh@example.com", "Password1", "MARKETING_USER");
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
        JsonNode tokens = registerUser("rotated@example.com", "Password1", "MARKETING_USER");
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
        JsonNode tokens = registerUser("expired@example.com", "Password1", "MARKETING_USER");
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
        JsonNode tokens = registerUser("valid@example.com", "Password1", "MARKETING_USER");
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
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson("admin@example.com", "Password1", "ADMIN")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.user.role").value("ADMIN"));
    }

    @Test
    void shouldReturnCorrectRole_whenRegisteringAsMarketingUser() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson("marketing@example.com", "Password1", "MARKETING_USER")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.user.role").value("MARKETING_USER"));
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
        JsonNode tokens = registerUser("logout@example.com", "Password1", "MARKETING_USER");
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

    // ---- Helpers ----

    private String registerJson(String email, String password, String role) {
        return """
                {
                    "firstName": "John",
                    "lastName": "Doe",
                    "email": "%s",
                    "password": "%s",
                    "companyId": %d,
                    "role": "%s"
                }
                """.formatted(email, password, companyId, role);
    }

    private JsonNode registerUser(String email, String password, String role) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson(email, password, role)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }
}
