package com.hawa.hawa_backend.postprovider.reddit;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "hawa.reddit")
public record RedditProperties(
        String clientId,
        String clientSecret,
        String userAgent,
        String baseUrl,
        String tokenUrl,
        int timeoutMs,
        int maxPostChars,
        int pageSize,
        int commentsPerSubmission,
        int maxSearchScan
) {
    public RedditProperties {
        if (userAgent == null || userAgent.isBlank()) {
            userAgent = "hawa-backend/0.1 (by u/unknown)";
        }
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = "https://oauth.reddit.com";
        }
        if (tokenUrl == null || tokenUrl.isBlank()) {
            tokenUrl = "https://www.reddit.com/api/v1/access_token";
        }
        if (timeoutMs <= 0) {
            timeoutMs = 15_000;
        }
        if (maxPostChars <= 0) {
            maxPostChars = 10_000;
        }
        if (pageSize <= 0 || pageSize > 100) {
            pageSize = 100;
        }
        if (commentsPerSubmission < 0) {
            commentsPerSubmission = 10;
        }
        if (maxSearchScan <= 0) {
            maxSearchScan = 1_000;
        }
    }
}
