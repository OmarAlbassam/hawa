package com.hawa.hawa_backend.statusindicator;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hawa.hawa_backend.auth.AuthenticatedUserService;
import com.hawa.hawa_backend.brand.Brand;
import com.hawa.hawa_backend.brand.BrandRepository;
import com.hawa.hawa_backend.enums.EmotionEnum;
import com.hawa.hawa_backend.enums.ReportStatusEnum;
import com.hawa.hawa_backend.exception.BadRequestException;
import com.hawa.hawa_backend.exception.ResourceNotFoundException;
import com.hawa.hawa_backend.report.Report;
import com.hawa.hawa_backend.report.ReportRepository;
import com.hawa.hawa_backend.review.ReviewRepository;
import com.hawa.hawa_backend.statusindicator.dto.StatusIndicatorResponse;
import com.hawa.hawa_backend.statusindicator.dto.StatusIndicatorResponse.EmotionShare;
import com.hawa.hawa_backend.statusindicator.dto.StatusIndicatorResponse.SentimentBreakdown;
import com.hawa.hawa_backend.statusindicator.dto.StatusIndicatorResponse.SentimentCategory;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class StatusIndicatorService {

    private static final BigDecimal SCORE_SCALE = new BigDecimal("20");
    private static final BigDecimal NEGATIVE_UPPER = new BigDecimal("40");
    private static final BigDecimal POSITIVE_LOWER = new BigDecimal("60");
    private static final int TOP_EMOTIONS_LIMIT = 3;

    private final AuthenticatedUserService authenticatedUserService;
    private final ReportRepository reportRepository;
    private final BrandRepository brandRepository;
    private final ReviewRepository reviewRepository;

    @Transactional(readOnly = true)
    public StatusIndicatorResponse getReportStatusIndicator(Long reportId) {
        Long companyId = authenticatedUserService.getCompanyId();
        log.debug("Fetching status indicator reportId={} companyId={}", reportId, companyId);

        Report report = reportRepository.findById(reportId)
                .filter(r -> r.getBrand().getCompany().getCompanyId().equals(companyId))
                .orElseThrow(() -> new ResourceNotFoundException("Report not found: " + reportId));

        if (report.getStatus() != ReportStatusEnum.COMPLETED) {
            throw new BadRequestException(
                    "Report is not ready (status: " + report.getStatus() + ")");
        }

        ReviewRepository.ReviewAggregate aggregate = reviewRepository.aggregateByReportId(reportId);
        List<ReviewRepository.EmotionCount> emotionCounts = reviewRepository.countByEmotion(reportId);
        ReviewRepository.SentimentBucketCount buckets =
                reviewRepository.countSentimentBucketsByReportId(reportId);

        return build(aggregate, emotionCounts, buckets, report.getSummary());
    }

    @Transactional(readOnly = true)
    public StatusIndicatorResponse getBrandStatusIndicator(Long brandId) {
        Long companyId = authenticatedUserService.getCompanyId();
        log.debug("Fetching status indicator brandId={} companyId={}", brandId, companyId);

        Brand brand = brandRepository.findById(brandId)
                .filter(b -> b.getCompany().getCompanyId().equals(companyId))
                .orElseThrow(() -> new ResourceNotFoundException("Brand not found: " + brandId));

        ReviewRepository.ReviewAggregate aggregate =
                reviewRepository.aggregateByBrandIdAndStatus(brand.getBrandId(), ReportStatusEnum.COMPLETED);
        List<ReviewRepository.EmotionCount> emotionCounts =
                reviewRepository.countByEmotionForBrandAndStatus(brand.getBrandId(), ReportStatusEnum.COMPLETED);
        ReviewRepository.SentimentBucketCount buckets =
                reviewRepository.countSentimentBucketsByBrandAndStatus(brand.getBrandId(), ReportStatusEnum.COMPLETED);

        String summary = reportRepository
                .findFirstByBrandBrandIdAndStatusOrderByFinishedAtDesc(
                        brand.getBrandId(), ReportStatusEnum.COMPLETED)
                .map(Report::getSummary)
                .orElse(null);

        return build(aggregate, emotionCounts, buckets, summary);
    }

    private StatusIndicatorResponse build(
            ReviewRepository.ReviewAggregate aggregate,
            List<ReviewRepository.EmotionCount> emotionCounts,
            ReviewRepository.SentimentBucketCount buckets,
            String summary) {

        long total = aggregate != null && aggregate.getTotalCount() != null
                ? aggregate.getTotalCount() : 0L;
        BigDecimal averageSentiment = total == 0 || aggregate == null
                ? null
                : aggregate.getAverageScore()
                        .multiply(SCORE_SCALE)
                        .setScale(2, RoundingMode.HALF_UP);

        SentimentCategory category = averageSentiment == null
                ? null
                : categorize(averageSentiment);

        SentimentBreakdown breakdown = new SentimentBreakdown(
                bucketValue(buckets == null ? null : buckets.getNegative()),
                bucketValue(buckets == null ? null : buckets.getNeutral()),
                bucketValue(buckets == null ? null : buckets.getPositive()));

        long emotionTotal = emotionCounts.stream().mapToLong(ReviewRepository.EmotionCount::getCount).sum();

        List<EmotionShare> topEmotions = emotionCounts.stream()
                .sorted(Comparator
                        .comparingLong(ReviewRepository.EmotionCount::getCount).reversed()
                        .thenComparing(ec -> ec.getKey().ordinal()))
                .limit(TOP_EMOTIONS_LIMIT)
                .map(ec -> new EmotionShare(
                        ec.getKey(),
                        ec.getCount(),
                        percentage(ec.getCount(), emotionTotal)))
                .toList();

        EmotionEnum dominantEmotion = topEmotions.isEmpty()
                ? null
                : topEmotions.get(0).emotion();

        BigDecimal diversity = total == 0
                ? null
                : normalizedShannonEntropy(emotionCounts, emotionTotal);

        return new StatusIndicatorResponse(
                averageSentiment,
                category,
                breakdown,
                dominantEmotion,
                topEmotions,
                diversity,
                summary,
                total);
    }

    private static SentimentCategory categorize(BigDecimal score) {
        if (score.compareTo(NEGATIVE_UPPER) <= 0) return SentimentCategory.NEGATIVE;
        if (score.compareTo(POSITIVE_LOWER) >= 0) return SentimentCategory.POSITIVE;
        return SentimentCategory.NEUTRAL;
    }

    private static long bucketValue(Long value) {
        return value == null ? 0L : value;
    }

    private static BigDecimal percentage(long count, long total) {
        if (total == 0) return BigDecimal.ZERO.setScale(2);
        return BigDecimal.valueOf(count)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP);
    }

    private static BigDecimal normalizedShannonEntropy(
            List<ReviewRepository.EmotionCount> emotionCounts, long total) {
        List<Long> counts = new ArrayList<>();
        for (ReviewRepository.EmotionCount ec : emotionCounts) {
            if (ec.getCount() != null && ec.getCount() > 0) counts.add(ec.getCount());
        }
        int n = counts.size();
        if (n <= 1 || total == 0) {
            return BigDecimal.ZERO.setScale(4);
        }
        double entropy = 0.0;
        for (long c : counts) {
            double p = (double) c / total;
            entropy -= p * Math.log(p);
        }
        double normalized = entropy / Math.log(n);
        return BigDecimal.valueOf(normalized).setScale(4, RoundingMode.HALF_UP);
    }
}
