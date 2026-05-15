package com.hawa.hawa_backend.postprovider.reddit.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record RedditCommentDto(
        @JsonProperty("id") String id,
        @JsonProperty("body") String body,
        @JsonProperty("permalink") String permalink,
        @JsonProperty("author") String author,
        @JsonProperty("created_utc") double createdUtc
) {
}
