package com.hawa.hawa_backend.llm.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record AnalyzeResult(
        @JsonProperty("post_id") Long postId,
        @JsonProperty("is_relevant") Boolean isRelevant,
        @JsonProperty("irrelevance_reason") String irrelevanceReason,
        @JsonProperty("score") Double score,
        @JsonProperty("llm_score") Double llmScore,
        @JsonProperty("emotion") String emotion,
        @JsonProperty("aspect") String aspect
) {
    public boolean relevant() {
        return isRelevant == null || isRelevant;
    }
}
