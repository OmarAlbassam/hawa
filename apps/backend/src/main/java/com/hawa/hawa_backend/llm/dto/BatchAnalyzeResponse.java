package com.hawa.hawa_backend.llm.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

public record BatchAnalyzeResponse(
        @JsonProperty("results") List<AnalyzeResult> results,
        @JsonProperty("failed") List<FailedResult> failed
) {}
