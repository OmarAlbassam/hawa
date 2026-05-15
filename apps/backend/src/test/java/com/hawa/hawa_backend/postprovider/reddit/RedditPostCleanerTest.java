package com.hawa.hawa_backend.postprovider.reddit;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RedditPostCleanerTest {

    private final RedditPostCleaner cleaner = new RedditPostCleaner(testProperties(10_000));

    private static RedditProperties testProperties(int maxPostChars) {
        return new RedditProperties(null, null, null, null, null, 0, maxPostChars, 0, 0, 0);
    }

    @Test
    void shouldReturnNull_whenSelftextIsDeleted() {
        assertThat(cleaner.clean("hi", "[deleted]")).isNull();
    }

    @Test
    void shouldReturnNull_whenSelftextIsRemoved() {
        assertThat(cleaner.clean("hi", "[removed]")).isNull();
    }

    @Test
    void shouldReturnNull_whenCombinedLengthBelowMinimum() {
        assertThat(cleaner.clean("hi", "no")).isNull();
    }

    @Test
    void shouldStripMarkdownLinks_keepingAnchorText() {
        String result = cleaner.clean("Check this", "I love [these shoes](https://example.com) a lot");
        assertThat(result).contains("these shoes").doesNotContain("https://example.com");
    }

    @Test
    void shouldStripBareUrls() {
        String result = cleaner.clean("Link drop", "visit https://reddit.com/r/x for more details here");
        assertThat(result).doesNotContain("https://reddit.com");
    }

    @Test
    void shouldStripRedditMentions() {
        String result = cleaner.clean("Thanks", "shoutout to /u/someone for the nice recommendation");
        assertThat(result).doesNotContain("/u/someone");
    }

    @Test
    void shouldCollapseWhitespaceAndCombine() {
        String result = cleaner.clean("Title here", "body   with\n\n\nlots of    whitespace");
        assertThat(result).isEqualTo("Title here body with lots of whitespace");
    }

    @Test
    void shouldDrop_whenExceedsMaxLength() {
        String longBody = "a".repeat(15_000);
        assertThat(cleaner.clean("Title long enough", longBody)).isNull();
    }

    @Test
    void shouldDrop_whenExceedsInjectedMaxPostChars() {
        RedditPostCleaner tightCleaner = new RedditPostCleaner(testProperties(200));
        String longBody = "a".repeat(5_000);
        assertThat(tightCleaner.clean("Title long enough", longBody)).isNull();
    }

    @Test
    void shouldKeep_whenWithinInjectedMaxPostChars() {
        RedditPostCleaner tightCleaner = new RedditPostCleaner(testProperties(200));
        String body = "b".repeat(150);
        String result = tightCleaner.clean("Title here", body);
        assertThat(result).isNotNull();
        assertThat(result.length()).isLessThanOrEqualTo(200);
    }

    @Test
    void shouldUseOnlyTitle_whenSelftextIsBlank() {
        String result = cleaner.clean("Great shoes, loved them", "");
        assertThat(result).isEqualTo("Great shoes, loved them");
    }
}
