package com.hawa.hawa_backend.post;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.hawa.hawa_backend.enums.RelevanceStatusEnum;

public interface PostRepository extends JpaRepository<Post, Long> {

    long countByReportBrandCompanyCompanyId(Long companyId);

    List<Post> findByReport_ReportId(Long reportId);
    long countByReportReportIdAndRelevanceStatus(Long reportId, RelevanceStatusEnum relevanceStatus);

    Page<Post> findByReportReportIdAndRelevanceStatus(Long reportId,
                                                     RelevanceStatusEnum relevanceStatus,
                                                     Pageable pageable);

    @Query(value = """
            SELECT p.* FROM post p
            WHERE p.report_id = :reportId
              AND p.relevance_status::text = CAST(:relevance AS text)
              AND (CAST(:language AS text) IS NULL OR p.language::text = CAST(:language AS text))
              AND (CAST(:dateFrom AS date) IS NULL OR p.created_at >= CAST(:dateFrom AS date))
              AND (CAST(:dateTo   AS date) IS NULL OR p.created_at <  (CAST(:dateTo AS date) + INTERVAL '1 day'))
            """,
            countQuery = """
            SELECT count(*) FROM post p
            WHERE p.report_id = :reportId
              AND p.relevance_status::text = CAST(:relevance AS text)
              AND (CAST(:language AS text) IS NULL OR p.language::text = CAST(:language AS text))
              AND (CAST(:dateFrom AS date) IS NULL OR p.created_at >= CAST(:dateFrom AS date))
              AND (CAST(:dateTo   AS date) IS NULL OR p.created_at <  (CAST(:dateTo AS date) + INTERVAL '1 day'))
            """,
            nativeQuery = true)
    Page<Post> findPostsWithFilters(@Param("reportId") Long reportId,
                                    @Param("relevance") String relevance,
                                    @Param("language") String language,
                                    @Param("dateFrom") LocalDate dateFrom,
                                    @Param("dateTo") LocalDate dateTo,
                                    Pageable pageable);

    @Query(value = """
            SELECT p.* FROM post p
            WHERE p.report_id = :reportId
              AND p.relevance_status::text = 'IRRELEVANT'
              AND (CAST(:language AS text) IS NULL OR p.language::text = CAST(:language AS text))
              AND (CAST(:dateFrom AS date) IS NULL OR p.created_at >= CAST(:dateFrom AS date))
              AND (CAST(:dateTo   AS date) IS NULL OR p.created_at <  (CAST(:dateTo AS date) + INTERVAL '1 day'))
            ORDER BY p.created_at ASC, p.post_id ASC
            """, nativeQuery = true)
    List<Post> findIrrelevantPostsList(@Param("reportId") Long reportId,
                                       @Param("language") String language,
                                       @Param("dateFrom") LocalDate dateFrom,
                                       @Param("dateTo") LocalDate dateTo);
}
