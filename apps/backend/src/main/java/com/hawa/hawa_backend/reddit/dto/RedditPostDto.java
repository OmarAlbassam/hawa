package com.hawa.hawa_backend.reddit.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record RedditPostDto(
        @JsonProperty("id") String id,
        @JsonProperty("title") String title,
        @JsonProperty("selftext") String selftext,
        @JsonProperty("url") String url,
        @JsonProperty("permalink") String permalink,
        @JsonProperty("created_utc") double createdUtc,
        @JsonProperty("subreddit") String subreddit,
        @JsonProperty("author") String author,
        @JsonProperty("over_18") boolean over18,
        @JsonProperty("link_flair_text") String linkFlairText
) {
}
