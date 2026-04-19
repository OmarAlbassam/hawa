package com.hawa.hawa_backend.analysis;

import java.util.ArrayList;
import java.util.List;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import com.hawa.hawa_backend.brand.Brand;
import com.hawa.hawa_backend.llm.LlmClient;
import com.hawa.hawa_backend.llm.LlmProperties;
import com.hawa.hawa_backend.llm.dto.AnalyzeResult;
import com.hawa.hawa_backend.llm.dto.BatchAnalyzeRequest;
import com.hawa.hawa_backend.llm.dto.BatchAnalyzeResponse;
import com.hawa.hawa_backend.llm.dto.FailedResult;
import com.hawa.hawa_backend.llm.dto.LlmPostDto;
import com.hawa.hawa_backend.post.Post;
import com.hawa.hawa_backend.post.collector.PostCollector;
import com.hawa.hawa_backend.post.collector.PostCollectorFactory;
import com.hawa.hawa_backend.report.Report;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class AnalysisJobRunner {

    private final AnalysisJobOperations operations;
    private final PostCollectorFactory postCollectorFactory;
    private final LlmClient llmClient;
    private final LlmProperties llmProperties;

    @Async("analysisExecutor")
    public void run(Long reportId) {
        log.info("Analysis job starting: reportId={}", reportId);
        try {
            Report report = operations.markProcessing(reportId);
            Brand brand = report.getBrand();

            PostCollector collector = postCollectorFactory.forDataSource(report.getDataSource());
            List<Post> posts = operations.collectAndPersistPosts(report, brand, collector);

            if (posts.isEmpty()) {
                operations.finalizeEmpty(reportId);
                log.info("Analysis job completed with no posts: reportId={}", reportId);
                return;
            }

            List<String> keywords = operations.loadKeywords(brand.getBrandId());
            List<AnalyzeResult> allResults = new ArrayList<>();
            List<FailedResult> allFailed = new ArrayList<>();
            int batchSize = llmProperties.batchSize();
            for (int i = 0; i < posts.size(); i += batchSize) {
                List<Post> chunk = posts.subList(i, Math.min(i + batchSize, posts.size()));
                BatchAnalyzeRequest request = toRequest(chunk, brand, keywords);
                BatchAnalyzeResponse response = llmClient.analyzeBatch(request);
                allResults.addAll(response.results());
                if (response.failed() != null) {
                    allFailed.addAll(response.failed());
                }
            }

            operations.persistReviews(posts, allResults);
            operations.finalizeCompleted(reportId, allResults, allFailed);
            log.info("Analysis job completed: reportId={} analyzed={} failed={}",
                    reportId, allResults.size(), allFailed.size());
        } catch (Exception ex) {
            log.error("Analysis job failed: reportId={}: {}", reportId, ex.getMessage(), ex);
            operations.markFailed(reportId, ex.getMessage());
        }
    }

    private static BatchAnalyzeRequest toRequest(List<Post> posts, Brand brand, List<String> keywords) {
        List<LlmPostDto> dtos = new ArrayList<>(posts.size());
        for (Post post : posts) {
            dtos.add(new LlmPostDto(post.getPostId(), post.getPostText()));
        }
        List<String> keywordsArg = (keywords == null || keywords.isEmpty()) ? null : keywords;
        return new BatchAnalyzeRequest(dtos, brand.getBrandName(), brand.getIndustry(), keywordsArg);
    }
}
