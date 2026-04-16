package com.hawa.hawa_backend.analysis.dto;

import java.time.LocalDate;

import com.hawa.hawa_backend.enums.DataSourceEnum;

import jakarta.validation.constraints.NotNull;

public record StartAnalysisRequest(
        @NotNull(message = "Data source is required")
        DataSourceEnum dataSource,

        LocalDate dateFrom,

        LocalDate dateTo
) {}
