package com.hawa.hawa_backend.statusindicator;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import com.hawa.hawa_backend.statusindicator.dto.StatusIndicatorResponse;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class StatusIndicatorController {

    private final StatusIndicatorService statusIndicatorService;

    @GetMapping("/api/reports/{reportId}/status-indicator")
    public ResponseEntity<StatusIndicatorResponse> getReportStatusIndicator(
            @PathVariable Long reportId) {
        return ResponseEntity.ok(statusIndicatorService.getReportStatusIndicator(reportId));
    }

    @GetMapping("/api/brands/{brandId}/status-indicator")
    public ResponseEntity<StatusIndicatorResponse> getBrandStatusIndicator(
            @PathVariable Long brandId) {
        return ResponseEntity.ok(statusIndicatorService.getBrandStatusIndicator(brandId));
    }
}
