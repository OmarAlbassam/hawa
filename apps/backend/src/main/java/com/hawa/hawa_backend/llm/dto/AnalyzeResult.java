package com.hawa.hawa_backend.llm.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record AnalyzeResult(
        @JsonProperty("post_id") Long postId,
        @JsonProperty("score") Double score,
        @JsonProperty("llm_score") Double llmScore,
        @JsonProperty("emotion") String emotion,
        @JsonProperty("aspect") String aspect
) {}
