package com.hawa.hawa_backend.llm.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record LlmPostDto(
        @JsonProperty("post_id") Long postId,
        @JsonProperty("text") String text
) {}
