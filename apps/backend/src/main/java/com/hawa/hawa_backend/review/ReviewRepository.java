package com.hawa.hawa_backend.review;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.hawa.hawa_backend.enums.AspectEnum;
import com.hawa.hawa_backend.enums.EmotionEnum;

public interface ReviewRepository extends JpaRepository<Review, Long> {

    interface ReviewAggregate {
        BigDecimal getAverageScore();
        BigDecimal getAverageConfidence();
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
            select avg(r.score)      as averageScore,
                   avg(r.confidence) as averageConfidence,
                   count(r)          as totalCount
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
}
