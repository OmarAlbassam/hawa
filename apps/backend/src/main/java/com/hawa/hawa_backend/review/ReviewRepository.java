package com.hawa.hawa_backend.review;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.hawa.hawa_backend.enums.AspectEnum;
import com.hawa.hawa_backend.enums.EmotionEnum;

public interface ReviewRepository extends JpaRepository<Review, Long> {

    interface RelevantPostProjection {
        Long getPostId();
        String getPostText();
        String getPostUrl();
        String getLanguage();
        BigDecimal getScore();
        String getEmotion();
        String getAspect();
        LocalDateTime getCreatedAt();
    }

    interface ReviewAggregate {
        BigDecimal getAverageScore();
        Long getTotalCount();
    }

    interface EmotionCount {
        EmotionEnum getKey();
        Long getCount();
    }

    interface AspectCount {
        AspectEnum getKey();
        Long getCount();
    }

    @Query("""
            select avg(r.score) as averageScore,
                   count(r)     as totalCount
            from Review r
            where r.post.report.reportId = :reportId
            """)
    ReviewAggregate aggregateByReportId(@Param("reportId") Long reportId);

    @Query("""
            select r.emotion as key, count(r) as count
            from Review r
            where r.post.report.reportId = :reportId and r.emotion is not null
            group by r.emotion
            """)
    List<EmotionCount> countByEmotion(@Param("reportId") Long reportId);

    @Query("""
            select r.aspect as key, count(r) as count
            from Review r
            where r.post.report.reportId = :reportId
            group by r.aspect
            """)
    List<AspectCount> countByAspect(@Param("reportId") Long reportId);

    @Query(value = """
            SELECT p.post_id        AS postId,
                   p.post_text      AS postText,
                   p.post_url       AS postUrl,
                   p.language::text AS language,
                   r.score          AS score,
                   r.emotion::text  AS emotion,
                   r.aspect::text   AS aspect,
                   p.created_at     AS createdAt
            FROM review r
            JOIN post p ON p.post_id = r.post_id
            WHERE p.report_id = :reportId
              AND (CAST(:sentimentMin AS numeric) IS NULL OR r.score >= CAST(:sentimentMin AS numeric))
              AND (CAST(:sentimentMax AS numeric) IS NULL OR r.score <= CAST(:sentimentMax AS numeric))
              AND (CAST(:emotion AS text) IS NULL OR r.emotion::text = CAST(:emotion AS text))
              AND (CAST(:aspect AS text) IS NULL OR r.aspect::text = CAST(:aspect AS text))
              AND (CAST(:language AS text) IS NULL OR p.language::text = CAST(:language AS text))
              AND (CAST(:dateFrom AS date) IS NULL OR p.created_at >= CAST(:dateFrom AS date))
              AND (CAST(:dateTo   AS date) IS NULL OR p.created_at <  (CAST(:dateTo AS date) + INTERVAL '1 day'))
            """,
            countQuery = """
            SELECT count(*)
            FROM review r
            JOIN post p ON p.post_id = r.post_id
            WHERE p.report_id = :reportId
              AND (CAST(:sentimentMin AS numeric) IS NULL OR r.score >= CAST(:sentimentMin AS numeric))
              AND (CAST(:sentimentMax AS numeric) IS NULL OR r.score <= CAST(:sentimentMax AS numeric))
              AND (CAST(:emotion AS text) IS NULL OR r.emotion::text = CAST(:emotion AS text))
              AND (CAST(:aspect AS text) IS NULL OR r.aspect::text = CAST(:aspect AS text))
              AND (CAST(:language AS text) IS NULL OR p.language::text = CAST(:language AS text))
              AND (CAST(:dateFrom AS date) IS NULL OR p.created_at >= CAST(:dateFrom AS date))
              AND (CAST(:dateTo   AS date) IS NULL OR p.created_at <  (CAST(:dateTo AS date) + INTERVAL '1 day'))
            """,
            nativeQuery = true)
    Page<RelevantPostProjection> findRelevantPostsForReport(
            @Param("reportId") Long reportId,
            @Param("sentimentMin") BigDecimal sentimentMin,
            @Param("sentimentMax") BigDecimal sentimentMax,
            @Param("emotion") String emotion,
            @Param("aspect") String aspect,
            @Param("language") String language,
            @Param("dateFrom") LocalDate dateFrom,
            @Param("dateTo") LocalDate dateTo,
            Pageable pageable);
}
