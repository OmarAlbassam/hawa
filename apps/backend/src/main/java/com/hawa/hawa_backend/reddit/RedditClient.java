package com.hawa.hawa_backend.reddit;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

import com.hawa.hawa_backend.reddit.dto.RedditListingResponse;
import com.hawa.hawa_backend.reddit.dto.RedditPostDto;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class RedditClient {

    private static final int MAX_QUERY_CHARS = 500;
    private static final long RATE_LIMIT_MAX_SLEEP_SECONDS = 5;

    private final RedditProperties properties;
    private final RedditTokenProvider tokenProvider;
    private final RestClient apiClient;

    public RedditClient(RedditProperties properties, RedditTokenProvider tokenProvider) {
        this.properties = properties;
        this.tokenProvider = tokenProvider;
        Duration timeout = Duration.ofMillis(properties.timeoutMs());
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(timeout);
        requestFactory.setReadTimeout(timeout);
        this.apiClient = RestClient.builder()
                .baseUrl(properties.baseUrl())
                .requestFactory(requestFactory)
                .defaultHeader(HttpHeaders.USER_AGENT, properties.userAgent())
                .build();
    }

    public List<RedditPostDto> searchPosts(String query, Instant dateFrom, Instant dateTo, int maxResults) {
        String trimmedQuery = trimQuery(query);
        List<RedditPostDto> collected = new ArrayList<>();
        String cursor = null;
        int pageSize = properties.pageSize();
        boolean stop = false;

        while (!stop && collected.size() < maxResults) {
            URI uri = buildSearchUri(trimmedQuery, pageSize, cursor);
            RedditListingResponse response = doSearchWithRetry(uri);

            if (response == null || response.data() == null || response.data().children() == null) {
                throw new RedditServiceException("Reddit returned empty body for /search");
            }

            List<RedditListingResponse.Child> children = response.data().children();
            if (children.isEmpty()) {
                break;
            }

            for (RedditListingResponse.Child child : children) {
                RedditPostDto post = child.data();
                if (post == null) {
                    continue;
                }
                Instant created = Instant.ofEpochSecond((long) post.createdUtc());
                if (created.isAfter(dateTo)) {
                    continue;
                }
                if (created.isBefore(dateFrom)) {
                    stop = true;
                    break;
                }
                collected.add(post);
                if (collected.size() >= maxResults) {
                    stop = true;
                    break;
                }
            }

            cursor = response.data().after();
            if (cursor == null || cursor.isBlank()) {
                break;
            }
        }

        log.info("Reddit search collected {} post(s) for query=\"{}\"", collected.size(), trimmedQuery);
        return collected;
    }

    private URI buildSearchUri(String query, int pageSize, String cursor) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString("/search")
                .queryParam("q", query)
                .queryParam("sort", "new")
                .queryParam("limit", pageSize)
                .queryParam("type", "link")
                .queryParam("restrict_sr", "false")
                .queryParam("t", "all")
                .queryParam("raw_json", 1);
        if (cursor != null && !cursor.isBlank()) {
            builder.queryParam("after", cursor);
        }
        return builder.build().encode().toUri();
    }

    private RedditListingResponse doSearchWithRetry(URI uri) {
        try {
            return executeSearch(uri, tokenProvider.getToken());
        } catch (HttpClientErrorException.Unauthorized ex) {
            log.warn("Reddit returned 401, refreshing token and retrying once");
            String refreshed = tokenProvider.forceRefresh();
            try {
                return executeSearch(uri, refreshed);
            } catch (RestClientException retryEx) {
                throw new RedditServiceException("Reddit /search failed after token refresh: "
                        + retryEx.getMessage(), retryEx);
            }
        } catch (HttpClientErrorException.TooManyRequests ex) {
            long resetSeconds = parseResetSeconds(ex.getResponseHeaders());
            if (resetSeconds > 0 && resetSeconds <= RATE_LIMIT_MAX_SLEEP_SECONDS) {
                log.warn("Reddit rate-limited; sleeping {}s before retry", resetSeconds);
                try {
                    Thread.sleep(Duration.ofSeconds(resetSeconds).toMillis());
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RedditServiceException("Interrupted while waiting for Reddit rate-limit reset", ie);
                }
                try {
                    return executeSearch(uri, tokenProvider.getToken());
                } catch (RestClientException retryEx) {
                    throw new RedditServiceException("Reddit /search failed after rate-limit retry: "
                            + retryEx.getMessage(), retryEx);
                }
            }
            throw new RedditServiceException("Reddit rate limit exceeded (reset in "
                    + resetSeconds + "s)", ex);
        } catch (HttpClientErrorException ex) {
            throw new RedditServiceException("Reddit /search client error "
                    + ex.getStatusCode() + ": " + ex.getMessage(), ex);
        } catch (HttpServerErrorException ex) {
            throw new RedditServiceException("Reddit /search server error "
                    + ex.getStatusCode() + ": " + ex.getMessage(), ex);
        } catch (RestClientException ex) {
            throw new RedditServiceException("Reddit /search call failed: " + ex.getMessage(), ex);
        }
    }

    private RedditListingResponse executeSearch(URI uri, String bearerToken) {
        return apiClient.get()
                .uri(uri)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + bearerToken)
                .retrieve()
                .onStatus(status -> status == HttpStatus.UNAUTHORIZED, (req, res) -> {
                    throw HttpClientErrorException.create(HttpStatus.UNAUTHORIZED,
                            "Unauthorized", res.getHeaders(), new byte[0], null);
                })
                .body(RedditListingResponse.class);
    }

    private long parseResetSeconds(HttpHeaders headers) {
        if (headers == null) {
            return 0;
        }
        String reset = headers.getFirst("x-ratelimit-reset");
        if (reset == null) {
            return 0;
        }
        try {
            return (long) Double.parseDouble(reset);
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private String trimQuery(String query) {
        if (query == null || query.isBlank()) {
            throw new RedditServiceException("Reddit query cannot be empty");
        }
        if (query.length() > MAX_QUERY_CHARS) {
            log.warn("Reddit query exceeded {} chars; truncating", MAX_QUERY_CHARS);
            return query.substring(0, MAX_QUERY_CHARS);
        }
        return query;
    }
}
