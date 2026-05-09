package com.hawa.hawa_backend.report;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.hawa.hawa_backend.enums.ReportStatusEnum;

public interface ReportRepository extends JpaRepository<Report, Long> {

    interface ReportListItem {
        Long getReportId();
        String getBrandName();
        String getStatus();
        String getDataSource();
        Integer getScore();
        String getSummary();
        LocalDate getDateFrom();
        LocalDate getDateTo();
        LocalDateTime getCreatedAt();
        LocalDateTime getFinishedAt();
    }

    interface ReportOverviewProjection {
        Long getReportId();
        String getBrandName();
        String getStatus();
        String getDataSource();
        LocalDate getDateFrom();
        LocalDate getDateTo();
        LocalDateTime getCreatedAt();
        LocalDateTime getFinishedAt();
        String getSummary();
        Integer getScore();
        long getAnalyzedCount();
        BigDecimal getAvgScore();
        long getIrrelevantCount();
        String getEmotionCountsJson();
        String getAspectCountsJson();
    }

    interface AnalyticsProjection {
        long getUsersCount();
        long getCompaniesCount();
        long getReportsCount();
        long getPostsCount();
        String getStatusCountsJson();
    }

    long countByStatus(ReportStatusEnum status);

    long countByBrandCompanyCompanyId(Long companyId);

    @Query(value = "SELECT r FROM Report r JOIN FETCH r.brand WHERE r.brand.company.companyId = :companyId",
            countQuery = "SELECT COUNT(r) FROM Report r WHERE r.brand.company.companyId = :companyId")
    Page<Report> findByCompanyId(@Param("companyId") Long companyId, Pageable pageable);

    @Query(value = """
            SELECT r.report_id        AS reportId,
                   b.brand_name       AS brandName,
                   r.status::text     AS status,
                   r.data_source::text AS dataSource,
                   r.score            AS score,
                   r.summary          AS summary,
                   r.date_from        AS dateFrom,
                   r.date_to          AS dateTo,
                   r.created_at       AS createdAt,
                   r.finished_at      AS finishedAt
            FROM report r
            JOIN brand b ON b.brand_id = r.brand_id
            WHERE b.company_id = :companyId
            AND (CAST(:brandId AS BIGINT) IS NULL OR r.brand_id = CAST(:brandId AS BIGINT))
            AND (CAST(:status AS report_status) IS NULL OR r.status = CAST(:status AS report_status))
            AND (CAST(:dateFrom AS DATE) IS NULL OR r.created_at >= CAST(:dateFrom AS DATE))
            AND (CAST(:dateTo AS DATE) IS NULL OR r.created_at < CAST(:dateTo AS DATE) + INTERVAL '1 day')
            """,
            countQuery = """
            SELECT COUNT(*) FROM report r
            JOIN brand b ON b.brand_id = r.brand_id
            WHERE b.company_id = :companyId
            AND (CAST(:brandId AS BIGINT) IS NULL OR r.brand_id = CAST(:brandId AS BIGINT))
            AND (CAST(:status AS report_status) IS NULL OR r.status = CAST(:status AS report_status))
            AND (CAST(:dateFrom AS DATE) IS NULL OR r.created_at >= CAST(:dateFrom AS DATE))
            AND (CAST(:dateTo AS DATE) IS NULL OR r.created_at < CAST(:dateTo AS DATE) + INTERVAL '1 day')
            """,
            nativeQuery = true)
    Page<ReportListItem> findByCompanyIdWithFilters(
            @Param("companyId") Long companyId,
            @Param("brandId") Long brandId,
            @Param("status") String status,
            @Param("dateFrom") LocalDate dateFrom,
            @Param("dateTo") LocalDate dateTo,
            Pageable pageable);

    @Query(value = """
            SELECT
              r.report_id          AS reportId,
              b.brand_name         AS brandName,
              r.status::text       AS status,
              r.data_source::text  AS dataSource,
              r.date_from          AS dateFrom,
              r.date_to            AS dateTo,
              r.created_at         AS createdAt,
              r.finished_at        AS finishedAt,
              r.summary            AS summary,
              r.score              AS score,
              COALESCE((SELECT COUNT(*) FROM review rv
                          JOIN post p ON p.post_id = rv.post_id
                        WHERE p.report_id = r.report_id), 0) AS analyzedCount,
              (SELECT AVG(rv.score) FROM review rv
                 JOIN post p ON p.post_id = rv.post_id
               WHERE p.report_id = r.report_id) AS avgScore,
              COALESCE((SELECT COUNT(*) FROM post
                        WHERE report_id = r.report_id
                          AND relevance_status = 'IRRELEVANT'::relevance_status), 0) AS irrelevantCount,
              (SELECT jsonb_object_agg(emotion, c)::text FROM (
                 SELECT rv.emotion::text AS emotion, COUNT(*) AS c
                 FROM review rv JOIN post p ON p.post_id = rv.post_id
                 WHERE p.report_id = r.report_id AND rv.emotion IS NOT NULL
                 GROUP BY rv.emotion) e) AS emotionCountsJson,
              (SELECT jsonb_object_agg(aspect, c)::text FROM (
                 SELECT rv.aspect::text AS aspect, COUNT(*) AS c
                 FROM review rv JOIN post p ON p.post_id = rv.post_id
                 WHERE p.report_id = r.report_id
                 GROUP BY rv.aspect) a) AS aspectCountsJson
            FROM report r JOIN brand b ON b.brand_id = r.brand_id
            WHERE r.report_id = :reportId AND b.company_id = :companyId
            """, nativeQuery = true)
    ReportOverviewProjection loadOverview(@Param("reportId") Long reportId,
                                          @Param("companyId") Long companyId);

    @Query(value = """
            SELECT
              r.report_id      AS reportId,
              r.status::text   AS status,
              (SELECT jsonb_agg(jsonb_build_object(
                        'aspect', g.aspect,
                        'emotion', g.emotion,
                        'count', g.c,
                        'avgScore', g.avg_score))::text
               FROM (
                 SELECT rv.aspect::text  AS aspect,
                        rv.emotion::text AS emotion,
                        COUNT(*)         AS c,
                        AVG(rv.score)    AS avg_score
                 FROM review rv JOIN post p ON p.post_id = rv.post_id
                 WHERE p.report_id = r.report_id
                 GROUP BY rv.aspect, rv.emotion
               ) g) AS breakdownJson
            FROM report r JOIN brand b ON b.brand_id = r.brand_id
            WHERE r.report_id = :reportId AND b.company_id = :companyId
            """, nativeQuery = true)
    AspectBreakdownProjection loadAspectBreakdown(@Param("reportId") Long reportId,
                                                  @Param("companyId") Long companyId);

    interface AspectBreakdownProjection {
        Long getReportId();
        String getStatus();
        String getBreakdownJson();
    }

    @Query(value = """
            SELECT
              (SELECT COUNT(*) FROM "user")  AS usersCount,
              (SELECT COUNT(*) FROM company) AS companiesCount,
              (SELECT COUNT(*) FROM report)  AS reportsCount,
              (SELECT COUNT(*) FROM post)    AS postsCount,
              (SELECT jsonb_object_agg(status, c)::text FROM (
                 SELECT status::text AS status, COUNT(*) AS c
                 FROM report GROUP BY status
               ) s) AS statusCountsJson
            """, nativeQuery = true)
    AnalyticsProjection loadAnalytics();
}
