package com.hawa.hawa_backend.analysis.dto;

import java.time.LocalDate;
import java.util.List;

import com.hawa.hawa_backend.enums.DataSourceEnum;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

public record StartAnalysisRequest(
        @NotNull(message = "Data source is required")
        DataSourceEnum dataSource,

        LocalDate dateFrom,

        LocalDate dateTo,

        @NotEmpty(message = "Select at least one keyword")
        List<Long> selectedKeywordIds,

        Boolean includeComments,

        @NotNull(message = "maxPosts is required")
        @Min(value = 1, message = "maxPosts must be at least 1")
        @Max(value = 500, message = "maxPosts must be at most 500")
        Integer maxPosts
) {}
