package com.hawa.hawa_backend.postprovider.reddit;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.concurrent.locks.ReentrantLock;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.hawa.hawa_backend.postprovider.reddit.dto.RedditTokenResponse;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class RedditTokenProvider {

    private final RedditProperties properties;
    private final RestClient tokenClient;
    private final ReentrantLock lock = new ReentrantLock();

    private volatile String token;
    private volatile Instant expiresAt = Instant.EPOCH;

    public RedditTokenProvider(RedditProperties properties) {
        this.properties = properties;
        Duration timeout = Duration.ofMillis(properties.timeoutMs());
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(timeout);
        requestFactory.setReadTimeout(timeout);
        String basic = Base64.getEncoder().encodeToString(
                (properties.clientId() + ":" + properties.clientSecret()).getBytes(StandardCharsets.UTF_8));
        this.tokenClient = RestClient.builder()
                .baseUrl(properties.tokenUrl())
                .requestFactory(requestFactory)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Basic " + basic)
                .defaultHeader(HttpHeaders.USER_AGENT, properties.userAgent())
                .build();
    }

    public String getToken() {
        if (token != null && Instant.now().isBefore(expiresAt.minusSeconds(60))) {
            return token;
        }
        return refresh();
    }

    public String forceRefresh() {
        lock.lock();
        try {
            token = null;
            expiresAt = Instant.EPOCH;
        } finally {
            lock.unlock();
        }
        return refresh();
    }

    private String refresh() {
        lock.lock();
        try {
            if (token != null && Instant.now().isBefore(expiresAt.minusSeconds(60))) {
                return token;
            }
            MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
            form.add("grant_type", "client_credentials");
            RedditTokenResponse response = tokenClient.post()
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(RedditTokenResponse.class);
            if (response == null || response.accessToken() == null || response.accessToken().isBlank()) {
                throw new RedditServiceException("Reddit token response was empty");
            }
            token = response.accessToken();
            expiresAt = Instant.now().plusSeconds(response.expiresIn() > 0 ? response.expiresIn() : 3600);
            log.info("Refreshed Reddit app-only token, expires in {}s", response.expiresIn());
            return token;
        } catch (RestClientException ex) {
            throw new RedditServiceException("Reddit token fetch failed: " + ex.getMessage(), ex);
        } finally {
            lock.unlock();
        }
    }
}
