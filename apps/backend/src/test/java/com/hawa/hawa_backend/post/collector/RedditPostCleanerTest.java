package com.hawa.hawa_backend.post.collector;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RedditPostCleanerTest {

    private final RedditPostCleaner cleaner = new RedditPostCleaner();

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
    void shouldTruncate_whenExceedsMaxLength() {
        String longBody = "a".repeat(15_000);
        String result = cleaner.clean("Title long enough", longBody);
        assertThat(result).hasSize(10_000);
    }

    @Test
    void shouldUseOnlyTitle_whenSelftextIsBlank() {
        String result = cleaner.clean("Great shoes, loved them", "");
        assertThat(result).isEqualTo("Great shoes, loved them");
    }
}
