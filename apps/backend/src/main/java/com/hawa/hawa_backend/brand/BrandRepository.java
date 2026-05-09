package com.hawa.hawa_backend.brand;

import java.math.BigDecimal;
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

    interface BrandStatusIndicatorProjection {
        Long getBrandId();
        String getBrandName();
        long getCompletedReportCount();
        BigDecimal getAvgScore();
        long getAnalyzedCount();
        long getNegativeCount();
        long getNeutralCount();
        long getPositiveCount();
        String getEmotionCountsJson();
        String getAspectCountsJson();
    }

    @Query(value = """
            SELECT
              b.brand_id   AS brandId,
              b.brand_name AS brandName,
              COALESCE((SELECT COUNT(*) FROM report r
                          WHERE r.brand_id = b.brand_id
                            AND r.status = 'COMPLETED'::report_status), 0) AS completedReportCount,
              (SELECT AVG(rv.score)
                 FROM review rv
                 JOIN post p   ON p.post_id   = rv.post_id
                 JOIN report r ON r.report_id = p.report_id
                WHERE r.brand_id = b.brand_id
                  AND r.status = 'COMPLETED'::report_status) AS avgScore,
              COALESCE((SELECT COUNT(*)
                          FROM review rv
                          JOIN post p   ON p.post_id   = rv.post_id
                          JOIN report r ON r.report_id = p.report_id
                         WHERE r.brand_id = b.brand_id
                           AND r.status = 'COMPLETED'::report_status), 0) AS analyzedCount,
              COALESCE((SELECT COUNT(*)
                          FROM review rv
                          JOIN post p   ON p.post_id   = rv.post_id
                          JOIN report r ON r.report_id = p.report_id
                         WHERE r.brand_id = b.brand_id
                           AND r.status = 'COMPLETED'::report_status
                           AND rv.score < 2.0), 0) AS negativeCount,
              COALESCE((SELECT COUNT(*)
                          FROM review rv
                          JOIN post p   ON p.post_id   = rv.post_id
                          JOIN report r ON r.report_id = p.report_id
                         WHERE r.brand_id = b.brand_id
                           AND r.status = 'COMPLETED'::report_status
                           AND rv.score >= 2.0 AND rv.score <= 3.0), 0) AS neutralCount,
              COALESCE((SELECT COUNT(*)
                          FROM review rv
                          JOIN post p   ON p.post_id   = rv.post_id
                          JOIN report r ON r.report_id = p.report_id
                         WHERE r.brand_id = b.brand_id
                           AND r.status = 'COMPLETED'::report_status
                           AND rv.score > 3.0), 0) AS positiveCount,
              (SELECT jsonb_object_agg(emotion, c)::text FROM (
                 SELECT rv.emotion::text AS emotion, COUNT(*) AS c
                 FROM review rv
                 JOIN post p   ON p.post_id   = rv.post_id
                 JOIN report r ON r.report_id = p.report_id
                 WHERE r.brand_id = b.brand_id
                   AND r.status = 'COMPLETED'::report_status
                   AND rv.emotion IS NOT NULL
                 GROUP BY rv.emotion) e) AS emotionCountsJson,
              (SELECT jsonb_object_agg(aspect, c)::text FROM (
                 SELECT rv.aspect::text AS aspect, COUNT(*) AS c
                 FROM review rv
                 JOIN post p   ON p.post_id   = rv.post_id
                 JOIN report r ON r.report_id = p.report_id
                 WHERE r.brand_id = b.brand_id
                   AND r.status = 'COMPLETED'::report_status
                 GROUP BY rv.aspect) a) AS aspectCountsJson
            FROM brand b
            WHERE b.brand_id = :brandId AND b.company_id = :companyId
            """, nativeQuery = true)
    BrandStatusIndicatorProjection loadStatusIndicator(@Param("brandId") Long brandId,
                                                       @Param("companyId") Long companyId);
}
