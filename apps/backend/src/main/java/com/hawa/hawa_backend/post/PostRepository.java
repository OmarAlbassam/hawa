package com.hawa.hawa_backend.post;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.hawa.hawa_backend.enums.RelevanceStatusEnum;

public interface PostRepository extends JpaRepository<Post, Long> {

    long countByReportBrandCompanyCompanyId(Long companyId);

    List<Post> findByReport_ReportId(Long reportId);
    long countByReportReportIdAndRelevanceStatus(Long reportId, RelevanceStatusEnum relevanceStatus);

    Page<Post> findByReportReportIdAndRelevanceStatus(Long reportId,
                                                     RelevanceStatusEnum relevanceStatus,
                                                     Pageable pageable);
}
