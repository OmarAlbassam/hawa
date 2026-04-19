package com.hawa.hawa_backend.brand;

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
import com.hawa.hawa_backend.enums.KeywordTypeEnum;
import com.hawa.hawa_backend.enums.UserRoleEnum;
import com.hawa.hawa_backend.keyword.Keyword;
import com.hawa.hawa_backend.keyword.KeywordRepository;
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
class BrandControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private CompanyRepository companyRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private BrandRepository brandRepository;
    @Autowired private KeywordRepository keywordRepository;
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

    @Nested
    class ListBrands {

        @Test
        void shouldReturnPaginatedBrands_forUserCompany() throws Exception {
            Brand brand = createBrand("Nike", company);
            createKeyword(brand, "nike", KeywordTypeEnum.BRAND_NAME);
            createKeyword(brand, "#justdoit", KeywordTypeEnum.HASHTAG);

            mockMvc.perform(get("/api/brands")
                            .header("Authorization", "Bearer " + userToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content").isArray())
                    .andExpect(jsonPath("$.content.length()").value(1))
                    .andExpect(jsonPath("$.content[0].brandId").value(brand.getBrandId()))
                    .andExpect(jsonPath("$.content[0].brandName").value("Nike"))
                    .andExpect(jsonPath("$.content[0].keywordCount").value(2))
                    .andExpect(jsonPath("$.totalElements").value(1));
        }

        @Test
        void shouldNotShowOtherCompanyBrands() throws Exception {
            Company otherCompany = new Company();
            otherCompany.setCompanyName("Other Corp");
            otherCompany = companyRepository.save(otherCompany);

            createBrand("Adidas", otherCompany);

            mockMvc.perform(get("/api/brands")
                            .header("Authorization", "Bearer " + userToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content").isEmpty());
        }

        @Test
        void shouldReturnEmptyPage_whenNoBrands() throws Exception {
            mockMvc.perform(get("/api/brands")
                            .header("Authorization", "Bearer " + userToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content").isEmpty())
                    .andExpect(jsonPath("$.totalElements").value(0));
        }
    }

    @Nested
    class GetBrandDetail {

        @Test
        void shouldReturnBrandWithKeywords() throws Exception {
            Brand brand = createBrand("Nike", company);
            createKeyword(brand, "nike", KeywordTypeEnum.BRAND_NAME);
            createKeyword(brand, "air max", KeywordTypeEnum.PRODUCT);

            mockMvc.perform(get("/api/brands/" + brand.getBrandId())
                            .header("Authorization", "Bearer " + userToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.brandId").value(brand.getBrandId()))
                    .andExpect(jsonPath("$.brandName").value("Nike"))
                    .andExpect(jsonPath("$.keywords").isArray())
                    .andExpect(jsonPath("$.keywords.length()").value(2))
                    .andExpect(jsonPath("$.keywords[0].keywordId").isNumber())
                    .andExpect(jsonPath("$.keywords[0].keyword").isString())
                    .andExpect(jsonPath("$.keywords[0].keywordType").isString());
        }

        @Test
        void shouldReturnBrand_withEmptyKeywords() throws Exception {
            Brand brand = createBrand("Nike", company);

            mockMvc.perform(get("/api/brands/" + brand.getBrandId())
                            .header("Authorization", "Bearer " + userToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.brandName").value("Nike"))
                    .andExpect(jsonPath("$.keywords").isEmpty());
        }

        @Test
        void shouldReject404_whenBrandNotFound() throws Exception {
            mockMvc.perform(get("/api/brands/99999")
                            .header("Authorization", "Bearer " + userToken))
                    .andExpect(status().isNotFound());
        }

        @Test
        void shouldReject400_whenBrandBelongsToOtherCompany() throws Exception {
            Company otherCompany = new Company();
            otherCompany.setCompanyName("Other Corp");
            otherCompany = companyRepository.save(otherCompany);

            Brand otherBrand = createBrand("Adidas", otherCompany);

            mockMvc.perform(get("/api/brands/" + otherBrand.getBrandId())
                            .header("Authorization", "Bearer " + userToken))
                    .andExpect(status().isBadRequest());
        }
    }

    // ==================== Keyword Management ====================

    @Nested
    class KeywordManagement {

        private Brand brand;

        @BeforeEach
        void setUpBrand() {
            brand = createBrand("Nike", company);
        }

        @Test
        void shouldCreateKeyword() throws Exception {
            mockMvc.perform(post("/api/brands/" + brand.getBrandId() + "/keywords")
                            .header("Authorization", "Bearer " + userToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"keyword": "nike", "keywordType": "BRAND_NAME"}
                                    """))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.keywordId").isNumber())
                    .andExpect(jsonPath("$.keyword").value("nike"))
                    .andExpect(jsonPath("$.keywordType").value("BRAND_NAME"))
                    .andExpect(jsonPath("$.brandId").value(brand.getBrandId()));
        }

        @Test
        void shouldReject400_whenKeywordBlank() throws Exception {
            mockMvc.perform(post("/api/brands/" + brand.getBrandId() + "/keywords")
                            .header("Authorization", "Bearer " + userToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"keyword": "", "keywordType": "BRAND_NAME"}
                                    """))
                    .andExpect(status().isBadRequest());
        }

        @Test
        void shouldListKeywords() throws Exception {
            createKeyword(brand, "nike", KeywordTypeEnum.BRAND_NAME);
            createKeyword(brand, "#justdoit", KeywordTypeEnum.HASHTAG);

            mockMvc.perform(get("/api/brands/" + brand.getBrandId() + "/keywords")
                            .header("Authorization", "Bearer " + userToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content.length()").value(2))
                    .andExpect(jsonPath("$.totalElements").value(2));
        }

        @Test
        void shouldUpdateKeyword() throws Exception {
            Keyword kw = createKeyword(brand, "old-name", KeywordTypeEnum.BRAND_NAME);

            mockMvc.perform(put("/api/brands/" + brand.getBrandId() + "/keywords/" + kw.getKeywordId())
                            .header("Authorization", "Bearer " + userToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"keyword": "new-name", "keywordType": "PRODUCT"}
                                    """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.keyword").value("new-name"))
                    .andExpect(jsonPath("$.keywordType").value("PRODUCT"));
        }

        @Test
        void shouldDeleteKeyword() throws Exception {
            Keyword kw = createKeyword(brand, "delete-me", KeywordTypeEnum.BRAND_NAME);

            mockMvc.perform(delete("/api/brands/" + brand.getBrandId() + "/keywords/" + kw.getKeywordId())
                            .header("Authorization", "Bearer " + userToken))
                    .andExpect(status().isNoContent());
        }

        @Test
        void shouldReject400_whenBrandBelongsToOtherCompany() throws Exception {
            Company otherCompany = new Company();
            otherCompany.setCompanyName("Other Corp");
            otherCompany = companyRepository.save(otherCompany);

            Brand otherBrand = createBrand("Adidas", otherCompany);

            mockMvc.perform(post("/api/brands/" + otherBrand.getBrandId() + "/keywords")
                            .header("Authorization", "Bearer " + userToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"keyword": "adidas", "keywordType": "BRAND_NAME"}
                                    """))
                    .andExpect(status().isBadRequest());
        }

        @Test
        void shouldReject404_whenKeywordNotFound() throws Exception {
            mockMvc.perform(put("/api/brands/" + brand.getBrandId() + "/keywords/99999")
                            .header("Authorization", "Bearer " + userToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"keyword": "x", "keywordType": "BRAND_NAME"}
                                    """))
                    .andExpect(status().isNotFound());
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

    private Brand createBrand(String name, Company targetCompany) {
        Brand brand = Brand.builder()
                .brandName(name)
                .company(targetCompany)
                .build();
        return brandRepository.save(brand);
    }

    private Keyword createKeyword(Brand brand, String keyword, KeywordTypeEnum type) {
        Keyword kw = Keyword.builder()
                .brand(brand)
                .keyword(keyword)
                .keywordType(type)
                .build();
        return keywordRepository.save(kw);
    }
}
