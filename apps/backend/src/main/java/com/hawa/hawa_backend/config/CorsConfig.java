package com.hawa.hawa_backend.config;

import org.springframework.context.annotation.Configuration;

@Configuration
public class CorsConfig {
    // CORS is now configured in SecurityConfig via the SecurityFilterChain.
    // This class is kept empty to avoid breaking any component scans.
}
