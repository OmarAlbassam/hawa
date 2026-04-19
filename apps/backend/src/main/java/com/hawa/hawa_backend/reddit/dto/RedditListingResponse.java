package com.hawa.hawa_backend.reddit.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record RedditListingResponse(String kind, Data data) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Data(String after, String before, List<Child> children) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Child(String kind, RedditPostDto data) {
    }
}
