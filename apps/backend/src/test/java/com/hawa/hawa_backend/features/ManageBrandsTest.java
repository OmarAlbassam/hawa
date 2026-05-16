package com.hawa.hawa_backend.features;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hawa.hawa_backend.TestcontainersConfiguration;
import com.hawa.hawa_backend.auth.JwtService;
import com.hawa.hawa_backend.brand.Brand;
import com.hawa.hawa_backend.brand.BrandRepository;
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
class ManageBrandsTest {

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
    private User adminUser;
    private String userToken;
    private String adminToken;
    private Long companyId;

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
        companyId = company.getCompanyId();

        marketingUser = createUser("marketing@example.com", UserRoleEnum.MARKETING_USER, company);
        userToken = jwtService.generateAccessToken(marketingUser);

        adminUser = createUser("admin@example.com", UserRoleEnum.ADMIN, null);
        adminToken = jwtService.generateAccessToken(adminUser);
    }

    @Nested
    class WhenListingBrandsAsMarketingUser {

        @Test
        void shouldReturnPaginatedBrands_whenScopedToUserCompany() throws Exception {
            Brand brand = createBrand("Nike", company);
            createKeyword(brand, "nike", KeywordTypeEnum.BRAND_NAME);
            createKeyword(brand, "nikee", KeywordTypeEnum.MISSPELLING);

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
        void shouldNotShowOtherCompanyBrands_whenScopedToUserCompany() throws Exception {
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
        void shouldReturnEmptyPage_whenCompanyHasNoBrands() throws Exception {
            mockMvc.perform(get("/api/brands")
                            .header("Authorization", "Bearer " + userToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content").isEmpty())
                    .andExpect(jsonPath("$.totalElements").value(0));
        }
    }

    @Nested
    class WhenGettingBrandDetail {

        @Test
        void shouldReturnBrandWithKeywords_whenBrandExists() throws Exception {
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
        void shouldReturnBrand_whenBrandHasNoKeywords() throws Exception {
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

    @Nested
    class WhenManagingKeywords {

        private Brand brand;

        @BeforeEach
        void setUpBrand() {
            brand = createBrand("Nike", company);
        }

        @Test
        void shouldCreateKeyword_whenRequestIsValid() throws Exception {
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
        void shouldListKeywords_whenBrandHasKeywords() throws Exception {
            createKeyword(brand, "nike", KeywordTypeEnum.BRAND_NAME);
            createKeyword(brand, "nikee", KeywordTypeEnum.MISSPELLING);

            mockMvc.perform(get("/api/brands/" + brand.getBrandId() + "/keywords")
                            .header("Authorization", "Bearer " + userToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content.length()").value(2))
                    .andExpect(jsonPath("$.totalElements").value(2));
        }

        @Test
        void shouldUpdateKeyword_whenKeywordExists() throws Exception {
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
        void shouldDeleteKeyword_whenKeywordExists() throws Exception {
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

    @Nested
    class WhenAdminManagesBrands {

        @Test
        void shouldCreateBrand_whenAdmin() throws Exception {
            mockMvc.perform(post("/api/admin/brands")
                            .header("Authorization", "Bearer " + adminToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"brandName": "Acme", "companyId": %d, "industry": "Tech"}
                                    """.formatted(companyId)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.brandId").isNumber())
                    .andExpect(jsonPath("$.brandName").value("Acme"))
                    .andExpect(jsonPath("$.company.companyId").value(companyId))
                    .andExpect(jsonPath("$.industry").value("Tech"));
        }

        @Test
        void shouldReject404_whenCreatingBrandWithInvalidCompany() throws Exception {
            mockMvc.perform(post("/api/admin/brands")
                            .header("Authorization", "Bearer " + adminToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"brandName": "Acme", "companyId": 99999}
                                    """))
                    .andExpect(status().isNotFound());
        }

        @Test
        void shouldListBrands_whenAdmin() throws Exception {
            createBrand("Test Brand", company);

            mockMvc.perform(get("/api/admin/brands")
                            .header("Authorization", "Bearer " + adminToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalElements").value(1))
                    .andExpect(jsonPath("$.content[0].brandName").value("Test Brand"));
        }

        @Test
        void shouldFilterBrandsByCompany_whenCompanyIdProvided() throws Exception {
            createBrand("Filtered Brand", company);

            Company other = new Company();
            other.setCompanyName("Other");
            other = companyRepository.save(other);

            createBrand("Other Brand", other);

            mockMvc.perform(get("/api/admin/brands")
                            .param("companyId", companyId.toString())
                            .header("Authorization", "Bearer " + adminToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalElements").value(1))
                    .andExpect(jsonPath("$.content[0].brandName").value("Filtered Brand"));
        }

        @Test
        void shouldUpdateBrand_whenAdmin() throws Exception {
            Brand brand = createBrand("Old Name", company);

            mockMvc.perform(put("/api/admin/brands/" + brand.getBrandId())
                            .header("Authorization", "Bearer " + adminToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"brandName": "New Name"}
                                    """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.brandName").value("New Name"));
        }

        @Test
        void shouldDeleteBrand_whenAdmin() throws Exception {
            Brand brand = createBrand("Delete Me", company);

            mockMvc.perform(delete("/api/admin/brands/" + brand.getBrandId())
                            .header("Authorization", "Bearer " + adminToken))
                    .andExpect(status().isNoContent());
        }
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

    private Keyword createKeyword(Brand brand, String keyword, KeywordTypeEnum type) {
        Keyword kw = Keyword.builder()
                .brand(brand)
                .keyword(keyword)
                .keywordType(type)
                .build();
        return keywordRepository.save(kw);
    }
}
