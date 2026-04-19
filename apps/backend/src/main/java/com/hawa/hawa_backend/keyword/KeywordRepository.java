package com.hawa.hawa_backend.keyword;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface KeywordRepository extends JpaRepository<Keyword, Long> {

    Page<Keyword> findByBrandBrandId(Long brandId, Pageable pageable);

    List<Keyword> findAllByBrandBrandId(Long brandId);

    @Query("SELECT k.brand.brandId, COUNT(k) FROM Keyword k WHERE k.brand.brandId IN :brandIds GROUP BY k.brand.brandId")
    List<Object[]> countByBrandIds(@Param("brandIds") List<Long> brandIds);

    long countByBrandBrandId(Long brandId);
}
