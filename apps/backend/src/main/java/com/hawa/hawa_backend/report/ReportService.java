package com.hawa.hawa_backend.report;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import com.hawa.hawa_backend.auth.AuthenticatedUserService;
import com.hawa.hawa_backend.enums.AspectEnum;
import com.hawa.hawa_backend.enums.DataSourceEnum;
import com.hawa.hawa_backend.enums.EmotionEnum;
import com.hawa.hawa_backend.enums.LanguageEnum;
import com.hawa.hawa_backend.enums.RelevanceStatusEnum;
import com.hawa.hawa_backend.enums.ReportStatusEnum;
import com.hawa.hawa_backend.exception.BadRequestException;
import com.hawa.hawa_backend.exception.ResourceNotFoundException;
import com.hawa.hawa_backend.post.Post;
import com.hawa.hawa_backend.post.PostRepository;
import com.hawa.hawa_backend.report.ReportRepository.AspectBreakdownProjection;
import com.hawa.hawa_backend.report.ReportRepository.ReportListItem;
import com.hawa.hawa_backend.report.ReportRepository.ReportOverviewProjection;
import com.hawa.hawa_backend.report.dto.AspectBreakdownResponse;
import com.hawa.hawa_backend.report.dto.AspectBreakdownResponse.AspectBreakdownItem;
import com.hawa.hawa_backend.report.dto.PostListItemResponse;
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

    private static final TypeReference<Map<String, Long>> STRING_LONG_MAP = new TypeReference<>() {};
    private static final TypeReference<List<AspectBreakdownRow>> BREAKDOWN_LIST = new TypeReference<>() {};

    private final AuthenticatedUserService authenticatedUserService;
    private final ReportRepository reportRepository;
    private final ReviewRepository reviewRepository;
    private final PostRepository postRepository;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public Page<ReportResponse> listReports(Long brandId, ReportStatusEnum status,
                                            LocalDate dateFrom, LocalDate dateTo,
                                            Pageable pageable) {
        Long companyId = authenticatedUserService.getCompanyId();
        log.debug("Listing reports for companyId={} with filters brandId={}, status={}, dateFrom={}, dateTo={}",
                companyId, brandId, status, dateFrom, dateTo);

        String statusStr = status != null ? status.name() : null;
        Pageable nativePageable = NativeSortUtil.toNativeSortPageable(pageable);
        return reportRepository.findByCompanyIdWithFilters(companyId, brandId, statusStr, dateFrom, dateTo, nativePageable)
                .map(ReportService::toReportResponse);
    }

    @Transactional(readOnly = true)
    public ReportOverviewResponse getReportOverview(Long reportId) {
        Long companyId = authenticatedUserService.getCompanyId();
        log.debug("Fetching report overview reportId={} companyId={}", reportId, companyId);

        ReportOverviewProjection p = reportRepository.loadOverview(reportId, companyId);
        if (p == null) {
            throw new ResourceNotFoundException("Report not found: " + reportId);
        }

        ReportStatusEnum status = ReportStatusEnum.valueOf(p.getStatus());
        if (status != ReportStatusEnum.COMPLETED) {
            throw new BadRequestException("Report is not ready (status: " + status + ")");
        }

        Map<EmotionEnum, Long> emotionDistribution = new EnumMap<>(EmotionEnum.class);
        for (EmotionEnum e : EmotionEnum.values()) emotionDistribution.put(e, 0L);
        for (Map.Entry<String, Long> entry : parseStringLongMap(p.getEmotionCountsJson()).entrySet()) {
            emotionDistribution.put(EmotionEnum.valueOf(entry.getKey()), entry.getValue());
        }

        Map<AspectEnum, Long> aspectDistribution = new EnumMap<>(AspectEnum.class);
        for (AspectEnum a : AspectEnum.values()) aspectDistribution.put(a, 0L);
        for (Map.Entry<String, Long> entry : parseStringLongMap(p.getAspectCountsJson()).entrySet()) {
            aspectDistribution.put(AspectEnum.valueOf(entry.getKey()), entry.getValue());
        }

        return new ReportOverviewResponse(
                p.getReportId(),
                p.getBrandName(),
                status,
                DataSourceEnum.valueOf(p.getDataSource()),
                p.getDateFrom(),
                p.getDateTo(),
                p.getCreatedAt(),
                p.getFinishedAt(),
                p.getSummary(),
                p.getScore(),
                p.getAnalyzedCount(),
                p.getIrrelevantCount(),
                p.getAvgScore(),
                emotionDistribution,
                aspectDistribution);
    }

    @Transactional(readOnly = true)
    public AspectBreakdownResponse getAspectBreakdown(Long reportId, AspectEnum aspectFilter) {
        Long companyId = authenticatedUserService.getCompanyId();
        log.debug("Computing aspect breakdown reportId={} companyId={} filter={}",
                reportId, companyId, aspectFilter);

        AspectBreakdownProjection projection = reportRepository.loadAspectBreakdown(reportId, companyId);
        if (projection == null) {
            throw new ResourceNotFoundException("Report not found: " + reportId);
        }

        ReportStatusEnum status = ReportStatusEnum.valueOf(projection.getStatus());
        if (status != ReportStatusEnum.COMPLETED) {
            throw new BadRequestException("Report is not ready (status: " + status + ")");
        }

        Map<AspectEnum, Long> counts = new EnumMap<>(AspectEnum.class);
        Map<AspectEnum, BigDecimal> totals = new EnumMap<>(AspectEnum.class);
        Map<AspectEnum, Map<EmotionEnum, Long>> emotionByAspect = new EnumMap<>(AspectEnum.class);
        for (AspectEnum a : AspectEnum.values()) {
            Map<EmotionEnum, Long> zeroed = new EnumMap<>(EmotionEnum.class);
            for (EmotionEnum e : EmotionEnum.values()) zeroed.put(e, 0L);
            emotionByAspect.put(a, zeroed);
            counts.put(a, 0L);
        }

        for (AspectBreakdownRow row : parseBreakdown(projection.getBreakdownJson())) {
            AspectEnum aspect = AspectEnum.valueOf(row.aspect);
            counts.merge(aspect, row.count, Long::sum);
            BigDecimal weighted = row.avgScore == null
                    ? BigDecimal.ZERO
                    : row.avgScore.multiply(BigDecimal.valueOf(row.count));
            totals.merge(aspect, weighted, BigDecimal::add);
            if (row.emotion != null) {
                emotionByAspect.get(aspect).merge(EmotionEnum.valueOf(row.emotion), row.count, Long::sum);
            }
        }

        List<AspectBreakdownItem> items = new ArrayList<>();
        for (AspectEnum a : AspectEnum.values()) {
            if (aspectFilter != null && a != aspectFilter) continue;
            long count = counts.getOrDefault(a, 0L);
            BigDecimal avg = count > 0
                    ? totals.get(a).divide(BigDecimal.valueOf(count), 4, java.math.RoundingMode.HALF_UP)
                    : null;
            items.add(new AspectBreakdownItem(a, count, avg, emotionByAspect.get(a)));
        }
        items.sort(Comparator
                .comparingLong(AspectBreakdownItem::postCount).reversed()
                .thenComparing(AspectBreakdownItem::aspect));

        return new AspectBreakdownResponse(projection.getReportId(), items);
    }

    @Transactional(readOnly = true)
    public Page<PostListItemResponse> listPosts(Long reportId,
                                                RelevanceStatusEnum relevance,
                                                BigDecimal sentimentMin,
                                                BigDecimal sentimentMax,
                                                EmotionEnum emotion,
                                                AspectEnum aspect,
                                                LanguageEnum language,
                                                LocalDate dateFrom,
                                                LocalDate dateTo,
                                                Pageable pageable) {
        Long companyId = authenticatedUserService.getCompanyId();
        Report report = reportRepository.findById(reportId)
                .filter(r -> r.getBrand().getCompany().getCompanyId().equals(companyId))
                .orElseThrow(() -> new ResourceNotFoundException("Report not found: " + reportId));
        log.debug("Listing posts reportId={} relevance={}", report.getReportId(), relevance);

        if (relevance == RelevanceStatusEnum.IRRELEVANT) {
            return postRepository.findPostsWithFilters(
                            reportId,
                            RelevanceStatusEnum.IRRELEVANT.name(),
                            language == null ? null : language.name(),
                            dateFrom,
                            dateTo,
                            toPostsPageable(pageable, /* hasReviewJoin */ false))
                    .map(ReportService::toIrrelevantItem);
        }
        return reviewRepository.findRelevantPostsForReport(
                        reportId,
                        sentimentMin,
                        sentimentMax,
                        emotion == null ? null : emotion.name(),
                        aspect == null ? null : aspect.name(),
                        language == null ? null : language.name(),
                        dateFrom,
                        dateTo,
                        toPostsPageable(pageable, /* hasReviewJoin */ true))
                .map(ReportService::toRelevantItem);
    }

    private static Pageable toPostsPageable(Pageable pageable, boolean hasReviewJoin) {
        if (pageable.getSort().isUnsorted()) {
            return pageable;
        }
        var orders = pageable.getSort().stream()
                .map(o -> {
                    String property = o.getProperty();
                    String mapped = switch (property) {
                        case "createdAt" -> hasReviewJoin ? "createdAt" : "created_at";
                        case "score" -> {
                            if (!hasReviewJoin) {
                                throw new BadRequestException(
                                        "Cannot sort by '" + property + "' for filtered-out posts");
                            }
                            yield property;
                        }
                        default -> throw new BadRequestException(
                                "Unsupported sort property: " + property);
                    };
                    return new Sort.Order(o.getDirection(), mapped);
                })
                .toList();
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), Sort.by(orders));
    }

    private static PostListItemResponse toRelevantItem(ReviewRepository.RelevantPostProjection p) {
        return new PostListItemResponse(
                p.getPostId(),
                p.getReviewId(),
                p.getPostText(),
                p.getPostUrl(),
                p.getLanguage() == null ? null : LanguageEnum.valueOf(p.getLanguage()),
                RelevanceStatusEnum.RELEVANT,
                null,
                p.getScore(),
                p.getEmotion() == null ? null : EmotionEnum.valueOf(p.getEmotion()),
                p.getAspect() == null ? null : AspectEnum.valueOf(p.getAspect()),
                p.getCreatedAt());
    }

    private static PostListItemResponse toIrrelevantItem(Post post) {
        return new PostListItemResponse(
                post.getPostId(),
                null,
                post.getPostText(),
                post.getPostUrl(),
                post.getLanguage(),
                post.getRelevanceStatus(),
                post.getIrrelevanceReason(),
                null, null, null,
                post.getCreatedAt());
    }

    private static ReportResponse toReportResponse(ReportListItem r) {
        return new ReportResponse(
                r.getReportId(),
                r.getBrandName(),
                ReportStatusEnum.valueOf(r.getStatus()),
                DataSourceEnum.valueOf(r.getDataSource()),
                r.getScore(),
                r.getSummary(),
                r.getDateFrom(),
                r.getDateTo(),
                r.getCreatedAt(),
                r.getFinishedAt());
    }

    private Map<String, Long> parseStringLongMap(String json) {
        if (json == null) return Collections.emptyMap();
        return objectMapper.readValue(json, STRING_LONG_MAP);
    }

    private List<AspectBreakdownRow> parseBreakdown(String json) {
        if (json == null) return List.of();
        return objectMapper.readValue(json, BREAKDOWN_LIST);
    }

    private static final class AspectBreakdownRow {
        public String aspect;
        public String emotion;
        public long count;
        public BigDecimal avgScore;
    }
}
