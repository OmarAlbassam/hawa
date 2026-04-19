package com.hawa.hawa_backend.dashboard;

import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hawa.hawa_backend.auth.AuthenticatedUserService;
import com.hawa.hawa_backend.brand.Brand;
import com.hawa.hawa_backend.brand.BrandRepository;
import com.hawa.hawa_backend.dashboard.dto.DashboardResponse;
import com.hawa.hawa_backend.dashboard.dto.DashboardResponse.BrandSummary;
import com.hawa.hawa_backend.dashboard.dto.DashboardResponse.DashboardStats;
import com.hawa.hawa_backend.dashboard.dto.DashboardResponse.RecentReport;
import com.hawa.hawa_backend.post.PostRepository;
import com.hawa.hawa_backend.report.Report;
import com.hawa.hawa_backend.report.ReportRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class DashboardService {

    private final AuthenticatedUserService authenticatedUserService;
    private final BrandRepository brandRepository;
    private final ReportRepository reportRepository;
    private final PostRepository postRepository;

    @Transactional(readOnly = true)
    public DashboardResponse getDashboard(int recentReportsLimit) {
        Long companyId = authenticatedUserService.getCompanyId();
        log.debug("Fetching dashboard for companyId={}", companyId);

        List<BrandSummary> brands = brandRepository.findAllByCompanyCompanyId(companyId)
                .stream()
                .map(this::toBrandSummary)
                .toList();

        PageRequest recentPage = PageRequest.of(0, recentReportsLimit, Sort.by(Sort.Direction.DESC, "createdAt"));
        List<RecentReport> recentReports = reportRepository.findByCompanyId(companyId, recentPage)
                .getContent()
                .stream()
                .map(this::toRecentReport)
                .toList();

        DashboardStats stats = new DashboardStats(
                reportRepository.countByBrandCompanyCompanyId(companyId),
                postRepository.countByReportBrandCompanyCompanyId(companyId),
                brandRepository.countByCompanyCompanyId(companyId));

        return new DashboardResponse(brands, recentReports, stats);
    }

    private BrandSummary toBrandSummary(Brand brand) {
        return new BrandSummary(
                brand.getBrandId(),
                brand.getBrandName(),
                brand.getIndustry(),
                brand.getStatusIndicator());
    }

    private RecentReport toRecentReport(Report report) {
        return new RecentReport(
                report.getReportId(),
                report.getBrand().getBrandName(),
                report.getStatus(),
                report.getDataSource(),
                report.getScore(),
                report.getCreatedAt(),
                report.getFinishedAt());
    }
}
