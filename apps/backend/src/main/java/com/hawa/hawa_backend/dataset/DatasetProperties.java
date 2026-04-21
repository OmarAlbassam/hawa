package com.hawa.hawa_backend.dataset;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "hawa.datasets")
public record DatasetProperties(int maxRows) {

    public DatasetProperties {
        if (maxRows <= 0) {
            maxRows = 10_000;
        }
    }
}
