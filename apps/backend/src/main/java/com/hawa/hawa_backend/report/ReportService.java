package com.hawa.hawa_backend.report;

import java.time.LocalDate;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hawa.hawa_backend.auth.AuthenticatedUserService;
import com.hawa.hawa_backend.brand.Brand;
import com.hawa.hawa_backend.brand.BrandRepository;
import com.hawa.hawa_backend.enums.ReportStatusEnum;
import com.hawa.hawa_backend.report.dto.ReportResponse;
import com.hawa.hawa_backend.util.NativeSortUtil;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReportService {

    private final AuthenticatedUserService authenticatedUserService;
    private final ReportRepository reportRepository;
    private final BrandRepository brandRepository;

    @Transactional(readOnly = true)
    public Page<ReportResponse> listReports(Long brandId, ReportStatusEnum status,
                                            LocalDate dateFrom, LocalDate dateTo,
                                            Pageable pageable) {
        Long companyId = authenticatedUserService.getCompanyId();
        log.debug("Listing reports for companyId={} with filters brandId={}, status={}, dateFrom={}, dateTo={}",
                companyId, brandId, status, dateFrom, dateTo);

        // Pre-fetch all brands for this company to avoid N+1 on report.getBrand()
        Map<Long, String> brandNames = brandRepository.findAllByCompanyCompanyId(companyId).stream()
                .collect(Collectors.toMap(Brand::getBrandId, Brand::getBrandName));

        String statusStr = status != null ? status.name() : null;
        Pageable nativePageable = NativeSortUtil.toNativeSortPageable(pageable);
        return reportRepository.findByCompanyIdWithFilters(companyId, brandId, statusStr, dateFrom, dateTo, nativePageable)
                .map(report -> toReportResponse(report, brandNames));
    }

    private ReportResponse toReportResponse(Report report, Map<Long, String> brandNames) {
        return new ReportResponse(
                report.getReportId(),
                brandNames.getOrDefault(report.getBrand().getBrandId(), "Unknown"),
                report.getStatus(),
                report.getDataSource(),
                report.getScore(),
                report.getSummary(),
                report.getDateFrom(),
                report.getDateTo(),
                report.getCreatedAt(),
                report.getFinishedAt());
    }
}
