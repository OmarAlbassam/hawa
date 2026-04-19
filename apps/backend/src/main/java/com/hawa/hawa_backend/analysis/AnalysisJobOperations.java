package com.hawa.hawa_backend.analysis;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.hawa.hawa_backend.brand.Brand;
import com.hawa.hawa_backend.enums.AspectEnum;
import com.hawa.hawa_backend.enums.EmotionEnum;
import com.hawa.hawa_backend.enums.ReportStatusEnum;
import com.hawa.hawa_backend.exception.ResourceNotFoundException;
import com.hawa.hawa_backend.keyword.Keyword;
import com.hawa.hawa_backend.keyword.KeywordRepository;
import com.hawa.hawa_backend.llm.dto.AnalyzeResult;
import com.hawa.hawa_backend.llm.dto.FailedResult;
import com.hawa.hawa_backend.post.Post;
import com.hawa.hawa_backend.post.PostRepository;
import com.hawa.hawa_backend.post.collector.PostCollector;
import com.hawa.hawa_backend.report.Report;
import com.hawa.hawa_backend.report.ReportRepository;
import com.hawa.hawa_backend.review.Review;
import com.hawa.hawa_backend.review.ReviewRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class AnalysisJobOperations {

    private static final int MAX_FAILURE_REASON_LENGTH = 500;

    private final ReportRepository reportRepository;
    private final PostRepository postRepository;
    private final ReviewRepository reviewRepository;
    private final KeywordRepository keywordRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Report markProcessing(Long reportId) {
        Report report = reportRepository.findById(reportId)
                .orElseThrow(() -> new ResourceNotFoundException("Report not found with id: " + reportId));
        report.setStatus(ReportStatusEnum.PROCESSING);
        // Touch lazy associations so they're usable after this tx closes.
        report.getBrand().getBrandName();
        return reportRepository.save(report);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public List<String> loadKeywords(Long brandId) {
        return keywordRepository.findAllByBrandBrandId(brandId).stream()
                .map(Keyword::getKeyword)
                .toList();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public List<Post> collectAndPersistPosts(Report report, Brand brand, PostCollector collector) {
        List<Post> collected = collector.collect(report, brand, report.getDateFrom(), report.getDateTo());
        if (collected.isEmpty()) {
            return List.of();
        }
        for (Post post : collected) {
            post.setReport(report);
        }
        return postRepository.saveAll(collected);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void persistReviews(List<Post> posts, List<AnalyzeResult> results) {
        Map<Long, Post> byId = new HashMap<>();
        for (Post post : posts) {
            byId.put(post.getPostId(), post);
        }
        List<Review> reviews = new ArrayList<>(results.size());
        for (AnalyzeResult result : results) {
            Post post = byId.get(result.postId());
            if (post == null) {
                log.warn("LLM returned result for unknown post_id={}, skipping", result.postId());
                continue;
            }
            reviews.add(Review.builder()
                    .post(post)
                    .llmScore(result.llmScore() == null ? null : BigDecimal.valueOf(result.llmScore()))
                    .score(BigDecimal.valueOf(result.score() == null ? 2.5 : result.score()))
                    .emotion(parseEmotion(result.emotion()))
                    .aspect(parseAspect(result.aspect()))
                    .confidence(BigDecimal.ONE)
                    .build());
        }
        reviewRepository.saveAll(reviews);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void finalizeEmpty(Long reportId) {
        Report report = reportRepository.findById(reportId)
                .orElseThrow(() -> new ResourceNotFoundException("Report not found with id: " + reportId));
        report.setStatus(ReportStatusEnum.COMPLETED);
        report.setSummary("No posts collected");
        report.setFinishedAt(LocalDateTime.now());
        reportRepository.save(report);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void finalizeCompleted(Long reportId, List<AnalyzeResult> results, List<FailedResult> failed) {
        Report report = reportRepository.findById(reportId)
                .orElseThrow(() -> new ResourceNotFoundException("Report not found with id: " + reportId));
        report.setStatus(ReportStatusEnum.COMPLETED);
        report.setScore(aggregateScore(results));
        report.setSummary(buildSummary(results, failed));
        report.setFinishedAt(LocalDateTime.now());
        if (!failed.isEmpty()) {
            report.setFailureReason(truncate(failed.size() + " post(s) failed LLM analysis"));
        }
        reportRepository.save(report);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(Long reportId, String message) {
        reportRepository.findById(reportId).ifPresent(report -> {
            report.setStatus(ReportStatusEnum.FAILED);
            report.setFailureReason(truncate(message));
            report.setFinishedAt(LocalDateTime.now());
            reportRepository.save(report);
        });
    }

    private static Integer aggregateScore(List<AnalyzeResult> results) {
        if (results.isEmpty()) {
            return null;
        }
        double sum = 0.0;
        int count = 0;
        for (AnalyzeResult r : results) {
            if (r.score() != null) {
                sum += r.score();
                count++;
            }
        }
        if (count == 0) {
            return null;
        }
        return BigDecimal.valueOf(sum / count).setScale(0, RoundingMode.HALF_UP).intValue();
    }

    private static String buildSummary(List<AnalyzeResult> results, List<FailedResult> failed) {
        Map<AspectEnum, Integer> aspectCounts = new EnumMap<>(AspectEnum.class);
        Map<EmotionEnum, Integer> emotionCounts = new EnumMap<>(EmotionEnum.class);
        for (AnalyzeResult r : results) {
            aspectCounts.merge(parseAspect(r.aspect()), 1, Integer::sum);
            EmotionEnum e = parseEmotion(r.emotion());
            if (e != null) {
                emotionCounts.merge(e, 1, Integer::sum);
            }
        }
        AspectEnum topAspect = topKey(aspectCounts);
        EmotionEnum topEmotion = topKey(emotionCounts);
        return "analyzed=" + results.size()
                + ", failed=" + failed.size()
                + ", top_aspect=" + (topAspect == null ? "n/a" : topAspect.name())
                + ", top_emotion=" + (topEmotion == null ? "n/a" : topEmotion.name());
    }

    private static <K> K topKey(Map<K, Integer> counts) {
        K best = null;
        int max = -1;
        for (Map.Entry<K, Integer> e : counts.entrySet()) {
            if (e.getValue() > max) {
                max = e.getValue();
                best = e.getKey();
            }
        }
        return best;
    }

    static AspectEnum parseAspect(String raw) {
        if (raw == null) {
            return AspectEnum.PRODUCT;
        }
        try {
            return AspectEnum.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            log.warn("Unknown aspect from LLM: '{}', defaulting to PRODUCT", raw);
            return AspectEnum.PRODUCT;
        }
    }

    static EmotionEnum parseEmotion(String raw) {
        if (raw == null) {
            return null;
        }
        try {
            return EmotionEnum.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            log.warn("Unknown emotion from LLM: '{}'", raw);
            return null;
        }
    }

    private static String truncate(String message) {
        if (message == null) {
            return null;
        }
        return message.length() <= MAX_FAILURE_REASON_LENGTH
                ? message
                : message.substring(0, MAX_FAILURE_REASON_LENGTH);
    }
}
