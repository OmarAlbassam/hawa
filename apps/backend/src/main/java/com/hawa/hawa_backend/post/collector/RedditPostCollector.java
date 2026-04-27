package com.hawa.hawa_backend.post.collector;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.hawa.hawa_backend.brand.Brand;
import com.hawa.hawa_backend.enums.DataSourceEnum;
import com.hawa.hawa_backend.enums.LanguageEnum;
import com.hawa.hawa_backend.post.Post;
import com.hawa.hawa_backend.reddit.RedditClient;
import com.hawa.hawa_backend.reddit.RedditProperties;
import com.hawa.hawa_backend.reddit.RedditQueryBuilder;
import com.hawa.hawa_backend.reddit.dto.RedditPostDto;
import com.hawa.hawa_backend.report.Report;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class RedditPostCollector implements PostCollector {

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

        List<RedditPostDto> rawPosts = redditClient.searchPosts(
                query, fromInstant, toInstant, properties.maxPostsPerReport());

        List<Post> posts = new ArrayList<>(rawPosts.size());
        Set<String> seenTexts = new HashSet<>();
        int droppedByCleaner = 0;
        int droppedByKeywordFilter = 0;
        int droppedAsDuplicate = 0;
        for (RedditPostDto dto : rawPosts) {
            String cleaned = cleaner.clean(dto.title(), dto.selftext());
            if (cleaned == null) {
                droppedByCleaner++;
                continue;
            }
            if (!containsAnyKeyword(cleaned, keywordPatterns)) {
                droppedByKeywordFilter++;
                continue;
            }
            if (!seenTexts.add(cleaned)) {
                droppedAsDuplicate++;
                continue;
            }
            posts.add(Post.builder()
                    .report(report)
                    .postText(cleaned)
                    .postUrl(buildPermalink(dto))
                    .language(detectLanguage(cleaned))
                    .build());
        }

        log.info("Reddit collection: reportId={}, query=\"{}\", fetched={}, kept={}, "
                        + "droppedByCleaner={}, droppedByKeywordFilter={}, droppedAsDuplicate={}",
                report.getReportId(), query, rawPosts.size(), posts.size(),
                droppedByCleaner, droppedByKeywordFilter, droppedAsDuplicate);
        return posts;
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
