package com.hawa.hawa_backend.postprovider.reddit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import com.hawa.hawa_backend.brand.Brand;
import com.hawa.hawa_backend.post.Post;
import com.hawa.hawa_backend.postprovider.reddit.dto.RedditPostDto;
import com.hawa.hawa_backend.report.Report;

@ExtendWith(MockitoExtension.class)
class RedditPostProviderTest {

    @Mock
    private RedditClient redditClient;

    @Spy
    private RedditPostCleaner cleaner = new RedditPostCleaner();

    @Mock
    private RedditProperties properties;

    @InjectMocks
    private RedditPostProvider provider;

    private Report reportWithKeyword(String keyword) {
        Report report = new Report();
        report.setReportId(1L);
        report.setSelectedKeywords(List.of(keyword));
        return report;
    }

    private RedditPostDto post(String title, String selftext) {
        return new RedditPostDto(
                "id", title, selftext, "https://example.com",
                "/r/test/comments/id/slug/", System.currentTimeMillis() / 1000.0,
                "test", "alice", false, null);
    }

    @Test
    void shouldKeepPost_whenKeywordAppearsAsWholeWordInBody() {
        when(properties.maxPostsPerReport()).thenReturn(100);
        when(redditClient.searchPosts(anyString(), any(), any(), anyInt())).thenReturn(List.of(
                post("Tried the new place", "Honestly the coffee here is excellent")));

        List<Post> posts = provider.collect(reportWithKeyword("coffee"),
                new Brand(), LocalDate.now().minusDays(1), LocalDate.now());

        assertThat(posts).hasSize(1);
        assertThat(posts.get(0).getPostText()).contains("coffee");
    }

    @Test
    void shouldDropPost_whenKeywordOnlyAppearedInUrlStrippedByCleaner() {
        when(properties.maxPostsPerReport()).thenReturn(100);
        // Cleaner strips bare URLs entirely; the keyword "coffee" only lives in the URL host.
        when(redditClient.searchPosts(anyString(), any(), any(), anyInt())).thenReturn(List.of(
                post("Random thoughts today", "Check this site https://coffee.example.com for details now")));

        List<Post> posts = provider.collect(reportWithKeyword("coffee"),
                new Brand(), LocalDate.now().minusDays(1), LocalDate.now());

        assertThat(posts).isEmpty();
    }

    @Test
    void shouldDropPost_whenKeywordIsOnlySubstringOfLongerWord() {
        when(properties.maxPostsPerReport()).thenReturn(100);
        when(redditClient.searchPosts(anyString(), any(), any(), anyInt())).thenReturn(List.of(
                post("Morning routine", "I went running this morning around the park area")));

        List<Post> posts = provider.collect(reportWithKeyword("run"),
                new Brand(), LocalDate.now().minusDays(1), LocalDate.now());

        assertThat(posts).isEmpty();
    }

    @Test
    void shouldMatchKeyword_whenCaseIsDifferent() {
        when(properties.maxPostsPerReport()).thenReturn(100);
        when(redditClient.searchPosts(anyString(), any(), any(), anyInt())).thenReturn(List.of(
                post("NIKE drop coming", "Saw the new shoes today, looked great")));

        List<Post> posts = provider.collect(reportWithKeyword("nike"),
                new Brand(), LocalDate.now().minusDays(1), LocalDate.now());

        assertThat(posts).hasSize(1);
    }

    @Test
    void shouldDropPost_whenSelftextIsRemovedAndTitleHasNoKeyword() {
        when(properties.maxPostsPerReport()).thenReturn(100);
        when(redditClient.searchPosts(anyString(), any(), any(), anyInt())).thenReturn(List.of(
                post("Some unrelated title here", "[removed]")));

        List<Post> posts = provider.collect(reportWithKeyword("coffee"),
                new Brand(), LocalDate.now().minusDays(1), LocalDate.now());

        assertThat(posts).isEmpty();
    }

    @Test
    void shouldDropDuplicatePosts_whenSameCleanedTextAppearsMultipleTimes() {
        when(properties.maxPostsPerReport()).thenReturn(100);
        // Three crossposts: distinct ids and permalinks but identical title + selftext.
        RedditPostDto a = new RedditPostDto("id-a", "Same headline shared widely",
                "Same body content here that is long enough to pass the cleaner",
                "https://example.com/a", "/r/sub1/comments/a/x/",
                System.currentTimeMillis() / 1000.0, "sub1", "alice", false, null);
        RedditPostDto b = new RedditPostDto("id-b", "Same headline shared widely",
                "Same body content here that is long enough to pass the cleaner",
                "https://example.com/b", "/r/sub2/comments/b/x/",
                System.currentTimeMillis() / 1000.0, "sub2", "bob", false, null);
        RedditPostDto c = new RedditPostDto("id-c", "Same headline shared widely",
                "Same body content here that is long enough to pass the cleaner",
                "https://example.com/c", "/r/sub3/comments/c/x/",
                System.currentTimeMillis() / 1000.0, "sub3", "carol", false, null);
        when(redditClient.searchPosts(anyString(), any(), any(), anyInt())).thenReturn(List.of(a, b, c));

        List<Post> posts = provider.collect(reportWithKeyword("headline"),
                new Brand(), LocalDate.now().minusDays(1), LocalDate.now());

        assertThat(posts).hasSize(1);
    }

    @Test
    void shouldMatchMultiWordKeyword_whenPhraseIsAdjacent() {
        when(properties.maxPostsPerReport()).thenReturn(100);
        when(redditClient.searchPosts(anyString(), any(), any(), anyInt())).thenReturn(List.of(
                post("Cafe review summary", "Ordered an iced latte and it was excellent today"),
                post("Other cafe notes here", "Iced tea and a regular latte separately ordered")));

        List<Post> posts = provider.collect(reportWithKeyword("iced latte"),
                new Brand(), LocalDate.now().minusDays(1), LocalDate.now());

        assertThat(posts).hasSize(1);
        assertThat(posts.get(0).getPostText()).contains("iced latte");
    }
}
