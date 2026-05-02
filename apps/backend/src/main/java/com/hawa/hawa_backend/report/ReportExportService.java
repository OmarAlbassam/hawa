package com.hawa.hawa_backend.report;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hawa.hawa_backend.auth.AuthenticatedUserService;
import com.hawa.hawa_backend.enums.AspectEnum;
import com.hawa.hawa_backend.enums.EmotionEnum;
import com.hawa.hawa_backend.enums.LanguageEnum;
import com.hawa.hawa_backend.enums.ReportStatusEnum;
import com.hawa.hawa_backend.exception.BadRequestException;
import com.hawa.hawa_backend.exception.ResourceNotFoundException;
import com.hawa.hawa_backend.post.Post;
import com.hawa.hawa_backend.post.PostRepository;
import com.hawa.hawa_backend.review.ReviewRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReportExportService {

    private static final String[] CSV_HEADERS = {
            "post_text", "sentiment_score", "emotion", "aspect", "language", "created_at"
    };

    private final AuthenticatedUserService authenticatedUserService;
    private final ReportRepository reportRepository;
    private final ReviewRepository reviewRepository;
    private final PostRepository postRepository;

    public record ExportFilters(
            BigDecimal sentimentMin,
            BigDecimal sentimentMax,
            EmotionEnum emotion,
            AspectEnum aspect,
            LanguageEnum language,
            LocalDate dateFrom,
            LocalDate dateTo) {

        boolean hasAnalysisFilter() {
            return sentimentMin != null || sentimentMax != null
                    || emotion != null || aspect != null;
        }
    }

    @Transactional(readOnly = true)
    public void writeReportCsv(Long reportId, ExportFilters filters, OutputStream out) throws IOException {
        Long companyId = authenticatedUserService.getCompanyId();
        log.info("Exporting report CSV: reportId={}", reportId);

        Report report = reportRepository.findById(reportId)
                .filter(r -> r.getBrand().getCompany().getCompanyId().equals(companyId))
                .orElseThrow(() -> new ResourceNotFoundException("Report not found: " + reportId));

        if (report.getStatus() != ReportStatusEnum.COMPLETED) {
            throw new BadRequestException(
                    "Report is not ready (status: " + report.getStatus() + ")");
        }

        List<ReviewRepository.RelevantPostProjection> relevantRows = reviewRepository.findRelevantPostsForReportList(
                reportId,
                filters.sentimentMin(),
                filters.sentimentMax(),
                filters.emotion() == null ? null : filters.emotion().name(),
                filters.aspect() == null ? null : filters.aspect().name(),
                filters.language() == null ? null : filters.language().name(),
                filters.dateFrom(),
                filters.dateTo());

        List<Post> irrelevantRows = filters.hasAnalysisFilter()
                ? List.of()
                : postRepository.findIrrelevantPostsList(
                        reportId,
                        filters.language() == null ? null : filters.language().name(),
                        filters.dateFrom(),
                        filters.dateTo());

        Writer writer = new OutputStreamWriter(out, StandardCharsets.UTF_8);
        CSVFormat format = CSVFormat.DEFAULT.builder().setHeader(CSV_HEADERS).build();
        try (CSVPrinter printer = new CSVPrinter(writer, format)) {
            for (ReviewRepository.RelevantPostProjection p : relevantRows) {
                printer.printRecord(
                        p.getPostText(),
                        p.getScore(),
                        p.getEmotion(),
                        p.getAspect(),
                        p.getLanguage(),
                        p.getCreatedAt());
            }
            for (Post post : irrelevantRows) {
                printer.printRecord(
                        post.getPostText(),
                        "",
                        "",
                        "",
                        post.getLanguage(),
                        post.getCreatedAt());
            }
            printer.flush();
        }
    }
}
