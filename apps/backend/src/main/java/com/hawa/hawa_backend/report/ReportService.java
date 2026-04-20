package com.hawa.hawa_backend.report;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hawa.hawa_backend.auth.AuthenticatedUserService;
import com.hawa.hawa_backend.brand.Brand;
import com.hawa.hawa_backend.brand.BrandRepository;
import com.hawa.hawa_backend.enums.AspectEnum;
import com.hawa.hawa_backend.enums.EmotionEnum;
import com.hawa.hawa_backend.enums.ReportStatusEnum;
import com.hawa.hawa_backend.exception.BadRequestException;
import com.hawa.hawa_backend.exception.ResourceNotFoundException;
import com.hawa.hawa_backend.report.dto.ReportOverviewResponse;
import com.hawa.hawa_backend.report.dto.ReportResponse;
import com.hawa.hawa_backend.review.ReviewRepository;
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
    private final ReviewRepository reviewRepository;

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

    @Transactional(readOnly = true)
    public ReportOverviewResponse getReportOverview(Long reportId) {
        Long companyId = authenticatedUserService.getCompanyId();
        log.debug("Fetching report overview reportId={} companyId={}", reportId, companyId);

        Report report = reportRepository.findById(reportId)
                .filter(r -> r.getBrand().getCompany().getCompanyId().equals(companyId))
                .orElseThrow(() -> new ResourceNotFoundException("Report not found: " + reportId));

        if (report.getStatus() != ReportStatusEnum.COMPLETED) {
            throw new BadRequestException(
                    "Report is not ready (status: " + report.getStatus() + ")");
        }

        ReviewRepository.ReviewAggregate aggregate = reviewRepository.aggregateByReportId(reportId);
        long totalPosts = aggregate != null && aggregate.getTotalCount() != null ? aggregate.getTotalCount() : 0L;
        BigDecimal averageSentiment = aggregate != null ? aggregate.getAverageScore() : null;
        BigDecimal averageConfidence = aggregate != null ? aggregate.getAverageConfidence() : null;

        Map<EmotionEnum, Long> emotionDistribution = new EnumMap<>(EmotionEnum.class);
        for (EmotionEnum e : EmotionEnum.values()) emotionDistribution.put(e, 0L);
        for (ReviewRepository.EmotionCount ec : reviewRepository.countByEmotion(reportId)) {
            emotionDistribution.put(ec.getKey(), ec.getCount());
        }

        Map<AspectEnum, Long> aspectDistribution = new EnumMap<>(AspectEnum.class);
        for (AspectEnum a : AspectEnum.values()) aspectDistribution.put(a, 0L);
        for (ReviewRepository.AspectCount ac : reviewRepository.countByAspect(reportId)) {
            aspectDistribution.put(ac.getKey(), ac.getCount());
        }

        return new ReportOverviewResponse(
                report.getReportId(),
                report.getBrand().getBrandName(),
                report.getStatus(),
                report.getDataSource(),
                report.getDateFrom(),
                report.getDateTo(),
                report.getCreatedAt(),
                report.getFinishedAt(),
                report.getSummary(),
                report.getScore(),
                totalPosts,
                averageSentiment,
                averageConfidence,
                emotionDistribution,
                aspectDistribution);
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
