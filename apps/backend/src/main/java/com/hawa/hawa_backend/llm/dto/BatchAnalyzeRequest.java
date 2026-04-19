package com.hawa.hawa_backend.llm.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record BatchAnalyzeRequest(
        @JsonProperty("posts") List<LlmPostDto> posts,
        @JsonProperty("brand_name") String brandName,
        @JsonProperty("brand_industry") String brandIndustry,
        @JsonProperty("keywords") List<String> keywords
) {}
