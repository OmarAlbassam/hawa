package com.hawa.hawa_backend.analysis;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.hawa.hawa_backend.enums.ReportStatusEnum;
import com.hawa.hawa_backend.report.ReportRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class AnalysisStartupRecovery implements ApplicationRunner {

    static final String FAILURE_REASON = "Interrupted by backend restart";

    private static final List<ReportStatusEnum> STUCK_STATUSES = List.of(
            ReportStatusEnum.PENDING,
            ReportStatusEnum.QUEUED,
            ReportStatusEnum.PROCESSING);

    private final ReportRepository reportRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        int updated = reportRepository.markStuckAsFailed(
                ReportStatusEnum.FAILED,
                STUCK_STATUSES,
                FAILURE_REASON,
                LocalDateTime.now());
        if (updated > 0) {
            log.info("Recovered {} stuck report(s) on startup: marked as FAILED", updated);
        }
    }
}
