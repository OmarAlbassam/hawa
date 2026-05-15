package com.hawa.hawa_backend.postprovider.reddit;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.hawa.hawa_backend.brand.Brand;
import com.hawa.hawa_backend.enums.DataSourceEnum;
import com.hawa.hawa_backend.enums.LanguageEnum;
import com.hawa.hawa_backend.post.Post;
import com.hawa.hawa_backend.postprovider.PostProvider;
import com.hawa.hawa_backend.postprovider.reddit.dto.RedditCommentDto;
import com.hawa.hawa_backend.postprovider.reddit.dto.RedditPostDto;
import com.hawa.hawa_backend.report.Report;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class RedditPostProvider extends PostProvider {

    private final RedditClient redditClient;
    private final RedditPostCleaner cleaner;
    private final RedditProperties properties;

    @Override
    public DataSourceEnum dataSource() {
        return DataSourceEnum.REDDIT;
    }

    @Override
    public List<Post> collect(Report report, Brand brand, LocalDate from, LocalDate to) {
        List<String> keywords = report.getSelectedKeywords();
        String query = RedditQueryBuilder.build(keywords);
        List<Pattern> keywordPatterns = compileKeywordPatterns(keywords);

        Instant fromInstant = (from != null ? from : LocalDate.now().minusDays(7))
                .atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant toInstant = (to != null ? to : LocalDate.now())
                .plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();

        int maxResults = report.getMaxPosts();
        boolean includeComments = report.isIncludeComments();
        int commentsPerSubmission = properties.commentsPerSubmission();

        List<Post> posts = new ArrayList<>(maxResults);
        Set<String> seenTexts = new HashSet<>();
        AtomicInteger droppedByCleaner = new AtomicInteger();
        AtomicInteger droppedByKeywordFilter = new AtomicInteger();
        AtomicInteger droppedAsDuplicate = new AtomicInteger();
        AtomicInteger submissionsKept = new AtomicInteger();
        AtomicInteger commentsKept = new AtomicInteger();

        redditClient.streamPosts(query, fromInstant, toInstant, properties.maxSearchScan(), dto -> {
            String cleanedSubmission = cleaner.clean(dto.title(), dto.selftext());
            AcceptResult submissionResult = tryAccept(
                    cleanedSubmission, buildPermalink(dto), report, posts, seenTexts, keywordPatterns);
            switch (submissionResult) {
                case CLEANER_DROPPED -> droppedByCleaner.incrementAndGet();
                case KEYWORD_DROPPED -> droppedByKeywordFilter.incrementAndGet();
                case DUPLICATE -> droppedAsDuplicate.incrementAndGet();
                case ACCEPTED -> submissionsKept.incrementAndGet();
            }
            if (posts.size() >= maxResults) {
                return false;
            }

            if (includeComments && commentsPerSubmission > 0) {
                List<RedditCommentDto> comments;
                try {
                    comments = redditClient.fetchComments(dto.id(), commentsPerSubmission);
                } catch (RedditServiceException ex) {
                    log.warn("Skipping comments for submission {}: {}", dto.id(), ex.getMessage());
                    return true;
                }
                for (RedditCommentDto comment : comments) {
                    String cleanedComment = cleaner.clean("", comment.body());
                    AcceptResult commentResult = tryAccept(
                            cleanedComment, buildCommentPermalink(comment), report, posts, seenTexts, keywordPatterns);
                    switch (commentResult) {
                        case CLEANER_DROPPED -> droppedByCleaner.incrementAndGet();
                        case KEYWORD_DROPPED -> droppedByKeywordFilter.incrementAndGet();
                        case DUPLICATE -> droppedAsDuplicate.incrementAndGet();
                        case ACCEPTED -> commentsKept.incrementAndGet();
                    }
                    if (posts.size() >= maxResults) {
                        return false;
                    }
                }
            }
            return true;
        });

        log.info("Reddit collection: reportId={}, query=\"{}\", kept={} "
                        + "(submissions={}, comments={}), includeComments={}, "
                        + "droppedByCleaner={}, droppedByKeywordFilter={}, droppedAsDuplicate={}",
                report.getReportId(), query, posts.size(),
                submissionsKept.get(), commentsKept.get(), includeComments,
                droppedByCleaner.get(), droppedByKeywordFilter.get(), droppedAsDuplicate.get());
        return posts;
    }

    private enum AcceptResult { ACCEPTED, CLEANER_DROPPED, KEYWORD_DROPPED, DUPLICATE }

    private AcceptResult tryAccept(String cleaned, String sourceUrl, Report report,
            List<Post> posts, Set<String> seenTexts, List<Pattern> keywordPatterns) {
        if (cleaned == null) {
            return AcceptResult.CLEANER_DROPPED;
        }
        if (!containsAnyKeyword(cleaned, keywordPatterns)) {
            return AcceptResult.KEYWORD_DROPPED;
        }
        if (!seenTexts.add(cleaned)) {
            return AcceptResult.DUPLICATE;
        }
        posts.add(Post.builder()
                .report(report)
                .postText(cleaned)
                .postUrl(sourceUrl)
                .language(detectLanguage(cleaned))
                .build());
        return AcceptResult.ACCEPTED;
    }

    private List<Pattern> compileKeywordPatterns(List<String> keywords) {
        List<Pattern> patterns = new ArrayList<>();
        for (String kw : keywords) {
            if (kw == null || kw.isBlank()) {
                continue;
            }
            String escaped = Pattern.quote(kw.trim().toLowerCase(Locale.ROOT));
            patterns.add(Pattern.compile("(?<![\\p{L}\\p{N}_])" + escaped + "(?![\\p{L}\\p{N}_])"));
        }
        return patterns;
    }

    private boolean containsAnyKeyword(String text, List<Pattern> patterns) {
        String lower = text.toLowerCase(Locale.ROOT);
        for (Pattern p : patterns) {
            if (p.matcher(lower).find()) {
                return true;
            }
        }
        return false;
    }

    private String buildPermalink(RedditPostDto dto) {
        if (dto.permalink() != null && !dto.permalink().isBlank()) {
            return "https://reddit.com" + dto.permalink();
        }
        return dto.url();
    }

    private String buildCommentPermalink(RedditCommentDto comment) {
        if (comment.permalink() != null && !comment.permalink().isBlank()) {
            return "https://reddit.com" + comment.permalink();
        }
        return null;
    }

    private LanguageEnum detectLanguage(String text) {
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c >= 0x0600 && c <= 0x06FF) {
                return LanguageEnum.AR;
            }
        }
        return LanguageEnum.EN;
    }
}
