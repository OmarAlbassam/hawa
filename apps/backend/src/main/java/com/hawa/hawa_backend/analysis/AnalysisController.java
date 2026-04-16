package com.hawa.hawa_backend.analysis;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.hawa.hawa_backend.analysis.dto.ReportStatusResponse;
import com.hawa.hawa_backend.analysis.dto.StartAnalysisRequest;
import com.hawa.hawa_backend.report.dto.ReportResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class AnalysisController {

    private final AnalysisService analysisService;

    @PostMapping("/brands/{brandId}/reports")
    public ResponseEntity<ReportResponse> startAnalysis(
            @PathVariable Long brandId,
            @Valid @RequestBody StartAnalysisRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(analysisService.startAnalysis(brandId, request));
    }

    @GetMapping("/reports/{reportId}/status")
    public ResponseEntity<ReportStatusResponse> getReportStatus(@PathVariable Long reportId) {
        return ResponseEntity.ok(analysisService.getReportStatus(reportId));
    }
}
