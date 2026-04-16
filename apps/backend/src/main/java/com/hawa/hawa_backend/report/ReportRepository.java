package com.hawa.hawa_backend.report;

import java.time.LocalDate;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.hawa.hawa_backend.enums.ReportStatusEnum;

public interface ReportRepository extends JpaRepository<Report, Long> {

    long countByStatus(ReportStatusEnum status);

    long countByBrandCompanyCompanyId(Long companyId);

    @Query(value = "SELECT r FROM Report r JOIN FETCH r.brand WHERE r.brand.company.companyId = :companyId",
            countQuery = "SELECT COUNT(r) FROM Report r WHERE r.brand.company.companyId = :companyId")
    Page<Report> findByCompanyId(@Param("companyId") Long companyId, Pageable pageable);

    @Query(value = """
            SELECT r.* FROM report r
            JOIN brand b ON b.brand_id = r.brand_id
            WHERE b.company_id = :companyId
            AND (CAST(:brandId AS BIGINT) IS NULL OR r.brand_id = CAST(:brandId AS BIGINT))
            AND (CAST(:status AS report_status) IS NULL OR r.status = CAST(:status AS report_status))
            AND (CAST(:dateFrom AS DATE) IS NULL OR r.created_at >= CAST(:dateFrom AS DATE))
            AND (CAST(:dateTo AS DATE) IS NULL OR r.created_at <= CAST(:dateTo AS DATE))
            """,
            countQuery = """
            SELECT COUNT(*) FROM report r
            JOIN brand b ON b.brand_id = r.brand_id
            WHERE b.company_id = :companyId
            AND (CAST(:brandId AS BIGINT) IS NULL OR r.brand_id = CAST(:brandId AS BIGINT))
            AND (CAST(:status AS report_status) IS NULL OR r.status = CAST(:status AS report_status))
            AND (CAST(:dateFrom AS DATE) IS NULL OR r.created_at >= CAST(:dateFrom AS DATE))
            AND (CAST(:dateTo AS DATE) IS NULL OR r.created_at <= CAST(:dateTo AS DATE))
            """,
            nativeQuery = true)
    Page<Report> findByCompanyIdWithFilters(
            @Param("companyId") Long companyId,
            @Param("brandId") Long brandId,
            @Param("status") String status,
            @Param("dateFrom") LocalDate dateFrom,
            @Param("dateTo") LocalDate dateTo,
            Pageable pageable);
}
