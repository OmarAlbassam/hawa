package com.hawa.hawa_backend.dashboard;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.hawa.hawa_backend.brand.Brand;

/**
 * Bound to Brand purely so Spring Data has a managed entity to attach the
 * EntityManager to — the actual query spans multiple tables.
 */
public interface DashboardRepository extends JpaRepository<Brand, Long> {

    interface DashboardProjection {
        String getBrandsJson();
        String getRecentReportsJson();
        long getReportsCount();
        long getPostsCount();
        long getBrandsCount();
    }

    @Query(value = """
            SELECT
              (SELECT jsonb_agg(jsonb_build_object(
                        'brandId', b.brand_id,
                        'brandName', b.brand_name,
                        'industry', b.industry,
                        'statusIndicator', b.status_indicator)
                      ORDER BY b.brand_id)
               FROM brand b WHERE b.company_id = :companyId)::text AS brands_json,

              (SELECT jsonb_agg(to_jsonb(t) ORDER BY t."createdAt" DESC)
               FROM (
                 SELECT r.report_id   AS "reportId",
                        b.brand_name  AS "brandName",
                        r.status      AS status,
                        r.data_source AS "dataSource",
                        r.score       AS score,
                        r.created_at  AS "createdAt",
                        r.finished_at AS "finishedAt"
                 FROM report r JOIN brand b ON b.brand_id = r.brand_id
                 WHERE b.company_id = :companyId
                 ORDER BY r.created_at DESC
                 LIMIT :recentLimit
               ) t)::text AS recent_reports_json,

              (SELECT COUNT(*) FROM report r JOIN brand b ON b.brand_id = r.brand_id
               WHERE b.company_id = :companyId) AS reports_count,

              (SELECT COUNT(*) FROM post p
                 JOIN report r ON r.report_id = p.report_id
                 JOIN brand  b ON b.brand_id  = r.brand_id
               WHERE b.company_id = :companyId) AS posts_count,

              (SELECT COUNT(*) FROM brand WHERE company_id = :companyId) AS brands_count
            """, nativeQuery = true)
    DashboardProjection loadDashboard(@Param("companyId") Long companyId,
                                      @Param("recentLimit") int recentLimit);
}
