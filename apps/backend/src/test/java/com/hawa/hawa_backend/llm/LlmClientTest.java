package com.hawa.hawa_backend.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.hawa.hawa_backend.llm.dto.BatchAnalyzeRequest;
import com.hawa.hawa_backend.llm.dto.BatchAnalyzeResponse;
import com.hawa.hawa_backend.llm.dto.LlmPostDto;

class LlmClientTest {

    private MockRestServiceServer server;
    private LlmClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://llm.test");
        server = MockRestServiceServer.bindTo(builder).build();
        client = new LlmClient(builder.build());
    }

    @Test
    void shouldMapBatchResponse_whenLlmReturnsResults() {
        server.expect(requestTo("http://llm.test/analyze/batch"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"post_id\":42")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"brand_name\":\"Nike\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"keywords\":[\"jordan\"]")))
                .andRespond(withSuccess("""
                        {
                          "results": [
                            {"post_id": 42, "score": 4.2, "llm_score": 4.2, "emotion": "JOY", "aspect": "PRODUCT"}
                          ],
                          "failed": []
                        }
                        """, MediaType.APPLICATION_JSON));

        BatchAnalyzeRequest request = new BatchAnalyzeRequest(
                List.of(new LlmPostDto(42L, "Great shoes")),
                "Nike", "Sportswear", List.of("jordan"));

        BatchAnalyzeResponse response = client.analyzeBatch(request);

        assertThat(response.results()).hasSize(1);
        assertThat(response.results().get(0).postId()).isEqualTo(42L);
        assertThat(response.results().get(0).llmScore()).isEqualTo(4.2);
        assertThat(response.results().get(0).emotion()).isEqualTo("JOY");
        assertThat(response.results().get(0).aspect()).isEqualTo("PRODUCT");
        assertThat(response.failed()).isEmpty();
        server.verify();
    }

    @Test
    void shouldThrowLlmServiceException_whenBodyIsEmpty() {
        server.expect(requestTo("http://llm.test/analyze/batch"))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        BatchAnalyzeRequest request = new BatchAnalyzeRequest(
                List.of(new LlmPostDto(1L, "t")), null, null, null);

        assertThatThrownBy(() -> client.analyzeBatch(request))
                .isInstanceOf(LlmServiceException.class)
                .hasMessageContaining("empty body");
    }

    @Test
    void shouldWrapRestClientException_whenServerReturns5xx() {
        server.expect(requestTo("http://llm.test/analyze/batch"))
                .andRespond(withServerError());

        BatchAnalyzeRequest request = new BatchAnalyzeRequest(
                List.of(new LlmPostDto(1L, "t")), null, null, null);

        assertThatThrownBy(() -> client.analyzeBatch(request))
                .isInstanceOf(LlmServiceException.class);
    }
}
