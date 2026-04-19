package com.hawa.hawa_backend.llm.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record FailedResult(
        @JsonProperty("post_id") Long postId,
        @JsonProperty("error") String error
) {}
