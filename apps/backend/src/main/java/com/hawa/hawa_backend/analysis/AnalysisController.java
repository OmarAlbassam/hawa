package com.hawa.hawa_backend.analysis;

import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.hawa.hawa_backend.analysis.dto.ReportStatusResponse;
import com.hawa.hawa_backend.analysis.dto.StartAnalysisRequest;
import com.hawa.hawa_backend.dataset.DatasetCsvParser;
import com.hawa.hawa_backend.dataset.dto.ParsedPost;
import com.hawa.hawa_backend.report.dto.ReportResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class AnalysisController {

    private final AnalysisService analysisService;
    private final DatasetCsvParser datasetCsvParser;

    @PostMapping("/brands/{brandId}/reports")
    public ResponseEntity<ReportResponse> startAnalysis(
            @PathVariable Long brandId,
            @Valid @RequestBody StartAnalysisRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(analysisService.startAnalysis(brandId, request));
    }

    @PostMapping(value = "/brands/{brandId}/reports/csv",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ReportResponse> startAnalysisFromCsv(
            @PathVariable Long brandId,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "dateFrom", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(value = "dateTo", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo) {
        List<ParsedPost> parsed = datasetCsvParser.parse(file);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(analysisService.startAnalysisFromCsv(brandId, parsed, dateFrom, dateTo));
    }

    @GetMapping("/reports/{reportId}/status")
    public ResponseEntity<ReportStatusResponse> getReportStatus(@PathVariable Long reportId) {
        return ResponseEntity.ok(analysisService.getReportStatus(reportId));
    }
}
