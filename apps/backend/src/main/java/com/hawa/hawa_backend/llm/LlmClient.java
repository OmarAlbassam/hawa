package com.hawa.hawa_backend.llm;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.hawa.hawa_backend.llm.dto.BatchAnalyzeRequest;
import com.hawa.hawa_backend.llm.dto.BatchAnalyzeResponse;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class LlmClient {

    private final RestClient restClient;

    @Autowired
    public LlmClient(LlmProperties properties) {
        this(buildRestClient(properties));
    }

    LlmClient(RestClient restClient) {
        this.restClient = restClient;
    }

    private static RestClient buildRestClient(LlmProperties properties) {
        Duration timeout = Duration.ofMillis(properties.timeoutMs());
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(timeout);
        requestFactory.setReadTimeout(timeout);
        return RestClient.builder()
                .baseUrl(properties.baseUrl())
                .requestFactory(requestFactory)
                .build();
    }

    public BatchAnalyzeResponse analyzeBatch(BatchAnalyzeRequest request) {
        log.debug("Calling LLM /analyze/batch with {} posts", request.posts().size());
        try {
            BatchAnalyzeResponse response = restClient.post()
                    .uri("/analyze/batch")
                    .body(request)
                    .retrieve()
                    .body(BatchAnalyzeResponse.class);
            if (response == null || response.results() == null) {
                throw new LlmServiceException("LLM returned empty body for /analyze/batch");
            }
            return response;
        } catch (RestClientException ex) {
            throw new LlmServiceException("LLM /analyze/batch call failed: " + ex.getMessage(), ex);
        }
    }
}
