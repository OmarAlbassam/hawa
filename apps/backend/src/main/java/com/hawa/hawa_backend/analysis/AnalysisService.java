package com.hawa.hawa_backend.analysis;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.hawa.hawa_backend.analysis.dto.ReportStatusResponse;
import com.hawa.hawa_backend.analysis.dto.StartAnalysisRequest;
import com.hawa.hawa_backend.auth.AuthenticatedUserService;
import com.hawa.hawa_backend.brand.Brand;
import com.hawa.hawa_backend.brand.BrandRepository;
import com.hawa.hawa_backend.dataset.dto.ParsedPost;
import com.hawa.hawa_backend.enums.DataSourceEnum;
import com.hawa.hawa_backend.enums.ReportStatusEnum;
import com.hawa.hawa_backend.exception.BadRequestException;
import com.hawa.hawa_backend.exception.ResourceNotFoundException;
import com.hawa.hawa_backend.post.Post;
import com.hawa.hawa_backend.post.PostRepository;
import com.hawa.hawa_backend.keyword.Keyword;
import com.hawa.hawa_backend.keyword.KeywordRepository;
import com.hawa.hawa_backend.report.Report;
import com.hawa.hawa_backend.report.ReportRepository;
import com.hawa.hawa_backend.report.dto.ReportResponse;
import com.hawa.hawa_backend.user.User;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class AnalysisService {

    private final AuthenticatedUserService authenticatedUserService;
    private final BrandRepository brandRepository;
    private final KeywordRepository keywordRepository;
    private final ReportRepository reportRepository;
    private final PostRepository postRepository;
    private final AnalysisJobRunner analysisJobRunner;

    @Transactional
    public ReportResponse startAnalysis(Long brandId, StartAnalysisRequest request) {
        User user = authenticatedUserService.getAuthenticatedUser();
        Long companyId = authenticatedUserService.getCompanyId();

        Brand brand = brandRepository.findById(brandId)
                .orElseThrow(() -> new ResourceNotFoundException("Brand not found with id: " + brandId));

        if (!brand.getCompany().getCompanyId().equals(companyId)) {
            throw new BadRequestException("Brand does not belong to your company");
        }

        List<String> selectedKeywords = loadSelectedKeywords(brand, request.selectedKeywordIds());

        Report report = Report.builder()
                .user(user)
                .brand(brand)
                .dataSource(request.dataSource())
                .status(ReportStatusEnum.PENDING)
                .dateFrom(request.dateFrom())
                .dateTo(request.dateTo())
                .selectedKeywords(selectedKeywords)
                .build();

        report = reportRepository.save(report);
        log.info("Analysis started: reportId={} for brand={} by user={}",
                report.getReportId(), brand.getBrandName(), user.getEmail());

        final Long reportId = report.getReportId();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                analysisJobRunner.run(reportId);
            }
        });

        return toReportResponse(report);
    }

    @Transactional
    public ReportResponse startAnalysisFromCsv(
            Long brandId, List<ParsedPost> parsedPosts, LocalDate dateFrom, LocalDate dateTo) {
        if (parsedPosts == null || parsedPosts.isEmpty()) {
            throw new BadRequestException("Dataset contains no posts");
        }

        User user = authenticatedUserService.getAuthenticatedUser();
        Long companyId = authenticatedUserService.getCompanyId();

        Brand brand = brandRepository.findById(brandId)
                .orElseThrow(() -> new ResourceNotFoundException("Brand not found with id: " + brandId));

        if (!brand.getCompany().getCompanyId().equals(companyId)) {
            throw new BadRequestException("Brand does not belong to your company");
        }

        Report report = Report.builder()
                .user(user)
                .brand(brand)
                .dataSource(DataSourceEnum.CSV_UPLOAD)
                .status(ReportStatusEnum.PENDING)
                .dateFrom(dateFrom)
                .dateTo(dateTo)
                .build();
        report = reportRepository.save(report);

        List<Post> posts = new ArrayList<>(parsedPosts.size());
        for (ParsedPost p : parsedPosts) {
            posts.add(Post.builder()
                    .report(report)
                    .postText(p.text())
                    .postUrl(p.url())
                    .language(p.language())
                    .build());
        }
        postRepository.saveAll(posts);

        log.info("CSV analysis started: reportId={} brand={} postCount={} by user={}",
                report.getReportId(), brand.getBrandName(), posts.size(), user.getEmail());

        final Long reportId = report.getReportId();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                analysisJobRunner.run(reportId);
            }
        });

        return toReportResponse(report);
    }

    @Transactional(readOnly = true)
    public ReportStatusResponse getReportStatus(Long reportId) {
        Long companyId = authenticatedUserService.getCompanyId();

        Report report = reportRepository.findById(reportId)
                .orElseThrow(() -> new ResourceNotFoundException("Report not found with id: " + reportId));

        if (!report.getBrand().getCompany().getCompanyId().equals(companyId)) {
            throw new BadRequestException("Report does not belong to your company");
        }

        return new ReportStatusResponse(
                report.getReportId(),
                report.getStatus(),
                report.getCreatedAt(),
                report.getFinishedAt(),
                report.getFailureReason());
    }

    private List<String> loadSelectedKeywords(Brand brand, List<Long> keywordIds) {
        List<Keyword> keywords = keywordRepository.findAllById(keywordIds);
        if (keywords.size() != keywordIds.size()) {
            throw new BadRequestException("One or more selected keywords do not exist");
        }
        for (Keyword keyword : keywords) {
            if (!keyword.getBrand().getBrandId().equals(brand.getBrandId())) {
                throw new BadRequestException("Selected keyword does not belong to the brand");
            }
        }
        return keywords.stream().map(Keyword::getKeyword).toList();
    }

    private ReportResponse toReportResponse(Report report) {
        return new ReportResponse(
                report.getReportId(),
                report.getBrand().getBrandName(),
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
