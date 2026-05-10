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
        int maxPostsPerReport,
        int pageSize
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
        if (maxPostsPerReport <= 0) {
            maxPostsPerReport = 200;
        }
        if (pageSize <= 0 || pageSize > 100) {
            pageSize = 100;
        }
    }
}
