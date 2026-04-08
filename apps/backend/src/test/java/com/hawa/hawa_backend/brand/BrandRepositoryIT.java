package com.hawa.hawa_backend.brand;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.hawa.hawa_backend.TestcontainersConfiguration;
import com.hawa.hawa_backend.company.Company;
import com.hawa.hawa_backend.company.CompanyRepository;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@Transactional
class BrandRepositoryIT {

    @Autowired
    private BrandRepository brandRepository;

    @Autowired
    private CompanyRepository companyRepository;

    private Company createCompany(String name) {
        Company company = new Company();
        company.setCompanyName(name);
        return companyRepository.saveAndFlush(company);
    }

    @Test
    void shouldFindBrandsByCompanyId() {
        Company company = createCompany("Test Corp");
        Brand brand = Brand.builder()
                .brandName("Acme")
                .company(company)
                .industry("Tech")
                .build();
        brandRepository.saveAndFlush(brand);

        Page<Brand> result = brandRepository.findByCompanyCompanyId(
                company.getCompanyId(), PageRequest.of(0, 10));

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().getFirst().getBrandName()).isEqualTo("Acme");
    }

    @Test
    void shouldReturnEmptyPageForUnknownCompany() {
        Page<Brand> result = brandRepository.findByCompanyCompanyId(
                999L, PageRequest.of(0, 10));

        assertThat(result.getContent()).isEmpty();
    }

    @Test
    void shouldFindAllWithCompany() {
        Company company = createCompany("Test Corp");
        Brand brand = Brand.builder()
                .brandName("GlobalBrand")
                .company(company)
                .build();
        brandRepository.saveAndFlush(brand);

        Page<Brand> result = brandRepository.findAllWithCompany(PageRequest.of(0, 10));

        assertThat(result.getContent()).isNotEmpty();
        assertThat(result.getContent().getFirst().getCompany()).isNotNull();
        assertThat(result.getContent().getFirst().getCompany().getCompanyName())
                .isEqualTo("Test Corp");
    }
}
