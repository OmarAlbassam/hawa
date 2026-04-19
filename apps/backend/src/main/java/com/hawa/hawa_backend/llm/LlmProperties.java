package com.hawa.hawa_backend.llm;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "hawa.llm")
public record LlmProperties(
        String baseUrl,
        int timeoutMs,
        int batchSize
) {
    public LlmProperties {
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = "http://localhost:8001";
        }
        if (timeoutMs <= 0) {
            timeoutMs = 30_000;
        }
        if (batchSize <= 0) {
            batchSize = 25;
        }
    }
}
