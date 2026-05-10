package com.hawa.hawa_backend.report;

import java.math.BigDecimal;
import java.time.LocalDate;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.hawa.hawa_backend.enums.AspectEnum;
import com.hawa.hawa_backend.enums.EmotionEnum;
import com.hawa.hawa_backend.enums.LanguageEnum;
import com.hawa.hawa_backend.enums.RelevanceStatusEnum;
import com.hawa.hawa_backend.enums.ReportStatusEnum;
import com.hawa.hawa_backend.report.dto.PostListItemResponse;
import com.hawa.hawa_backend.report.dto.ReportOverviewResponse;
import com.hawa.hawa_backend.report.dto.ReportResponse;
import com.hawa.hawa_backend.report.dto.StatusIndicatorResponse;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;
    private final ReportExportService reportExportService;

    @GetMapping
    public ResponseEntity<Page<ReportResponse>> listReports(
            @RequestParam(required = false) Long brandId,
            @RequestParam(required = false) ReportStatusEnum status,
            @RequestParam(required = false) LocalDate dateFrom,
            @RequestParam(required = false) LocalDate dateTo,
            Pageable pageable) {
        return ResponseEntity.ok(reportService.listReports(brandId, status, dateFrom, dateTo, pageable));
    }

    @GetMapping("/{reportId}")
    public ResponseEntity<ReportOverviewResponse> getReportOverview(@PathVariable Long reportId) {
        return ResponseEntity.ok(reportService.getReportOverview(reportId));
    }

    @GetMapping("/{reportId}/export")
    public ResponseEntity<byte[]> exportReportCsv(
            @PathVariable Long reportId,
            @RequestParam(required = false) BigDecimal sentimentMin,
            @RequestParam(required = false) BigDecimal sentimentMax,
            @RequestParam(required = false) EmotionEnum emotion,
            @RequestParam(required = false) AspectEnum aspect,
            @RequestParam(required = false) LanguageEnum language,
            @RequestParam(required = false) LocalDate dateFrom,
            @RequestParam(required = false) LocalDate dateTo) {
        ReportExportService.ExportFilters filters = new ReportExportService.ExportFilters(
                sentimentMin, sentimentMax, emotion, aspect, language, dateFrom, dateTo);

        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try {
            reportExportService.writeReportCsv(reportId, filters, buffer);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }

        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=\"report-" + reportId + ".csv\"");
        return ResponseEntity.ok()
                .headers(headers)
                .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
                .body(buffer.toByteArray());
    }

    @GetMapping("/{reportId}/posts")
    public ResponseEntity<Page<PostListItemResponse>> listPosts(
            @PathVariable Long reportId,
            @RequestParam(defaultValue = "RELEVANT") RelevanceStatusEnum relevance,
            @RequestParam(required = false) BigDecimal sentimentMin,
            @RequestParam(required = false) BigDecimal sentimentMax,
            @RequestParam(required = false) EmotionEnum emotion,
            @RequestParam(required = false) AspectEnum aspect,
            @RequestParam(required = false) LanguageEnum language,
            @RequestParam(required = false) LocalDate dateFrom,
            @RequestParam(required = false) LocalDate dateTo,
            Pageable pageable) {
        return ResponseEntity.ok(reportService.listPosts(
                reportId, relevance, sentimentMin, sentimentMax, emotion, aspect,
                language, dateFrom, dateTo, pageable));
    }

    @GetMapping("/{reportId}/status-indicator")
    public ResponseEntity<StatusIndicatorResponse> getStatusIndicator(@PathVariable Long reportId) {
        return ResponseEntity.ok(reportService.getStatusIndicator(reportId));
    }
}
