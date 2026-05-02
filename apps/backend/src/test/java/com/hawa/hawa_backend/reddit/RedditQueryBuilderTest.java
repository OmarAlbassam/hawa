package com.hawa.hawa_backend.reddit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

class RedditQueryBuilderTest {

    @Test
    void shouldFieldRestrictAndOrJoinKeywords() {
        String query = RedditQueryBuilder.build(List.of("Nike", "jordan", "air max"));

        assertThat(query).isEqualTo(
                "(title:\"Nike\" OR selftext:\"Nike\")"
                        + " OR (title:\"jordan\" OR selftext:\"jordan\")"
                        + " OR (title:\"air max\" OR selftext:\"air max\")");
    }

    @Test
    void shouldQuoteKeywordsContainingSpaces() {
        String query = RedditQueryBuilder.build(List.of("Coca Cola"));
        assertThat(query).isEqualTo("(title:\"Coca Cola\" OR selftext:\"Coca Cola\")");
    }

    @Test
    void shouldQuoteKeywordsContainingHyphen() {
        String query = RedditQueryBuilder.build(List.of("e-commerce"));
        assertThat(query).isEqualTo("(title:\"e-commerce\" OR selftext:\"e-commerce\")");
    }

    @Test
    void shouldDropKeywords_whenExceedingBudget() {
        List<String> keywords = new ArrayList<>();
        keywords.add("anchor");
        String longTerm = "x".repeat(60);
        for (int i = 0; i < 20; i++) {
            keywords.add(longTerm + i);
        }

        String query = RedditQueryBuilder.build(keywords);

        assertThat(query.length()).isLessThanOrEqualTo(500);
        assertThat(query).startsWith("(title:\"anchor\" OR selftext:\"anchor\")");
    }

    @Test
    void shouldIgnoreBlankKeywords() {
        String query = RedditQueryBuilder.build(List.of("  ", "real"));

        assertThat(query).isEqualTo("(title:\"real\" OR selftext:\"real\")");
    }

    @Test
    void shouldThrow_whenKeywordListIsEmpty() {
        assertThatThrownBy(() -> RedditQueryBuilder.build(List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldThrow_whenAllKeywordsAreBlank() {
        assertThatThrownBy(() -> RedditQueryBuilder.build(List.of("  ", "")))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
