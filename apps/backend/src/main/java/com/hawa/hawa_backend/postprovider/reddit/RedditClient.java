package com.hawa.hawa_backend.postprovider.reddit;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

import com.hawa.hawa_backend.postprovider.reddit.dto.RedditCommentDto;
import com.hawa.hawa_backend.postprovider.reddit.dto.RedditCommentListingResponse;
import com.hawa.hawa_backend.postprovider.reddit.dto.RedditListingResponse;
import com.hawa.hawa_backend.postprovider.reddit.dto.RedditPostDto;

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

    /**
     * Paginate Reddit search and hand each post to {@code consumer}. The consumer returns
     * {@code true} to keep streaming or {@code false} to stop. Pagination also stops when
     * Reddit runs out of results, the date window is exited, or {@code maxScan} raw posts
     * have been examined (safety cap to bound API calls when the consumer is very selective).
     */
    public void streamPosts(String query, Instant dateFrom, Instant dateTo, int maxScan,
            Predicate<RedditPostDto> consumer) {
        String trimmedQuery = trimQuery(query);
        Set<String> seenIds = new HashSet<>();
        int duplicates = 0;
        int delivered = 0;
        int scanned = 0;
        String cursor = null;
        int pageSize = properties.pageSize();
        boolean stop = false;

        while (!stop) {
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
                if (post.id() != null && !seenIds.add(post.id())) {
                    duplicates++;
                    continue;
                }
                scanned++;
                delivered++;
                if (!consumer.test(post)) {
                    stop = true;
                    break;
                }
                if (maxScan > 0 && scanned >= maxScan) {
                    stop = true;
                    break;
                }
            }

            cursor = response.data().after();
            if (cursor == null || cursor.isBlank()) {
                break;
            }
        }

        log.info("Reddit search streamed {} post(s) for query=\"{}\" (duplicates={}, scanned={})",
                delivered, trimmedQuery, duplicates, scanned);
    }

    public List<RedditCommentDto> fetchComments(String submissionId, int limit) {
        if (submissionId == null || submissionId.isBlank() || limit <= 0) {
            return List.of();
        }
        URI uri = buildCommentsUri(submissionId, limit);
        RedditCommentListingResponse[] response = doCommentsWithRetry(uri);
        if (response == null || response.length < 2 || response[1] == null
                || response[1].data() == null || response[1].data().children() == null) {
            return List.of();
        }

        List<RedditCommentDto> comments = new ArrayList<>();
        for (RedditCommentListingResponse.Child child : response[1].data().children()) {
            if (child == null || !"t1".equals(child.kind()) || child.data() == null) {
                continue;
            }
            RedditCommentDto comment = child.data();
            if (comment.body() == null || comment.body().isBlank()) {
                continue;
            }
            comments.add(comment);
            if (comments.size() >= limit) {
                break;
            }
        }
        log.debug("Reddit fetched {} comment(s) for submission {}", comments.size(), submissionId);
        return comments;
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

    private URI buildCommentsUri(String submissionId, int limit) {
        return UriComponentsBuilder.fromUriString("/comments/" + submissionId)
                .queryParam("limit", limit)
                .queryParam("depth", 1)
                .queryParam("sort", "top")
                .queryParam("raw_json", 1)
                .build()
                .encode()
                .toUri();
    }

    private RedditCommentListingResponse[] doCommentsWithRetry(URI uri) {
        try {
            return executeComments(uri, tokenProvider.getToken());
        } catch (HttpClientErrorException.Unauthorized ex) {
            log.warn("Reddit returned 401 on /comments, refreshing token and retrying once");
            String refreshed = tokenProvider.forceRefresh();
            try {
                return executeComments(uri, refreshed);
            } catch (RestClientException retryEx) {
                throw new RedditServiceException("Reddit /comments failed after token refresh: "
                        + retryEx.getMessage(), retryEx);
            }
        } catch (HttpClientErrorException.TooManyRequests ex) {
            long resetSeconds = parseResetSeconds(ex.getResponseHeaders());
            if (resetSeconds > 0 && resetSeconds <= RATE_LIMIT_MAX_SLEEP_SECONDS) {
                log.warn("Reddit /comments rate-limited; sleeping {}s before retry", resetSeconds);
                try {
                    Thread.sleep(Duration.ofSeconds(resetSeconds).toMillis());
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RedditServiceException("Interrupted while waiting for Reddit rate-limit reset", ie);
                }
                try {
                    return executeComments(uri, tokenProvider.getToken());
                } catch (RestClientException retryEx) {
                    throw new RedditServiceException("Reddit /comments failed after rate-limit retry: "
                            + retryEx.getMessage(), retryEx);
                }
            }
            throw new RedditServiceException("Reddit rate limit exceeded on /comments (reset in "
                    + resetSeconds + "s)", ex);
        } catch (HttpClientErrorException ex) {
            throw new RedditServiceException("Reddit /comments client error "
                    + ex.getStatusCode() + ": " + ex.getMessage(), ex);
        } catch (HttpServerErrorException ex) {
            throw new RedditServiceException("Reddit /comments server error "
                    + ex.getStatusCode() + ": " + ex.getMessage(), ex);
        } catch (RestClientException ex) {
            throw new RedditServiceException("Reddit /comments call failed: " + ex.getMessage(), ex);
        }
    }

    private RedditCommentListingResponse[] executeComments(URI uri, String bearerToken) {
        return apiClient.get()
                .uri(uri)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + bearerToken)
                .retrieve()
                .onStatus(status -> status == HttpStatus.UNAUTHORIZED, (req, res) -> {
                    throw HttpClientErrorException.create(HttpStatus.UNAUTHORIZED,
                            "Unauthorized", res.getHeaders(), new byte[0], null);
                })
                .body(RedditCommentListingResponse[].class);
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
