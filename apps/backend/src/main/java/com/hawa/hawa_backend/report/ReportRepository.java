package com.hawa.hawa_backend.report;

import org.springframework.data.jpa.repository.JpaRepository;

import com.hawa.hawa_backend.enums.ReportStatusEnum;

public interface ReportRepository extends JpaRepository<Report, Long> {

    long countByStatus(ReportStatusEnum status);
}
