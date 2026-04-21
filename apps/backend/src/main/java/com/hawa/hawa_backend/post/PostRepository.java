package com.hawa.hawa_backend.post;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PostRepository extends JpaRepository<Post, Long> {

    long countByReportBrandCompanyCompanyId(Long companyId);

    List<Post> findByReport_ReportId(Long reportId);
}
