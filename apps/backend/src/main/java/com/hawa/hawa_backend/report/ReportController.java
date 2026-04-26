package com.hawa.hawa_backend.report;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.hawa.hawa_backend.enums.AspectEnum;
import com.hawa.hawa_backend.enums.EmotionEnum;
import com.hawa.hawa_backend.enums.RelevanceStatusEnum;
import com.hawa.hawa_backend.enums.ReportStatusEnum;
import com.hawa.hawa_backend.report.dto.PostListItemResponse;
import com.hawa.hawa_backend.report.dto.ReportOverviewResponse;
import com.hawa.hawa_backend.report.dto.ReportResponse;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;

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

    @GetMapping("/{reportId}/posts")
    public ResponseEntity<Page<PostListItemResponse>> listPosts(
            @PathVariable Long reportId,
            @RequestParam(defaultValue = "RELEVANT") RelevanceStatusEnum relevance,
            @RequestParam(required = false) BigDecimal sentimentMin,
            @RequestParam(required = false) BigDecimal sentimentMax,
            @RequestParam(required = false) EmotionEnum emotion,
            @RequestParam(required = false) AspectEnum aspect,
            Pageable pageable) {
        return ResponseEntity.ok(reportService.listPosts(
                reportId, relevance, sentimentMin, sentimentMax, emotion, aspect, pageable));
    }
}
