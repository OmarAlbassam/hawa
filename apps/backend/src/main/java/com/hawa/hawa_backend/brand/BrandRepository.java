package com.hawa.hawa_backend.brand;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BrandRepository extends JpaRepository<Brand, Long> {

    @Query(value = "SELECT b FROM Brand b JOIN FETCH b.company WHERE b.company.companyId = :companyId",
            countQuery = "SELECT COUNT(b) FROM Brand b WHERE b.company.companyId = :companyId")
    Page<Brand> findByCompanyCompanyId(@Param("companyId") Long companyId, Pageable pageable);

    @Query(value = "SELECT b FROM Brand b JOIN FETCH b.company",
            countQuery = "SELECT COUNT(b) FROM Brand b")
    Page<Brand> findAllWithCompany(Pageable pageable);

    long countByCompanyCompanyId(Long companyId);

    List<Brand> findAllByCompanyCompanyId(Long companyId);
}
