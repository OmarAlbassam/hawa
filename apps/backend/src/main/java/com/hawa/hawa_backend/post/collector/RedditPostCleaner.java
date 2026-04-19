package com.hawa.hawa_backend.post.collector;

import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

@Component
public class RedditPostCleaner {

    private static final int MIN_LENGTH = 10;
    private static final int MAX_LENGTH = 10_000;

    private static final Pattern MARKDOWN_LINK = Pattern.compile("\\[([^\\]]+)\\]\\([^\\)]+\\)");
    private static final Pattern BARE_URL = Pattern.compile("https?://\\S+");
    private static final Pattern REDDIT_MENTION = Pattern.compile("\\b/?[ru]/\\w+\\b");
    private static final Pattern BLOCKQUOTE = Pattern.compile("(?m)^>\\s*");
    private static final Pattern HEADING = Pattern.compile("(?m)^#+\\s*");
    private static final Pattern EMPHASIS = Pattern.compile("[*_]{1,3}(\\S[^*_]*\\S|\\S)[*_]{1,3}");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    public String clean(String title, String selftext) {
        String safeTitle = title == null ? "" : title;
        String safeBody = (selftext == null
                || "[deleted]".equals(selftext.trim())
                || "[removed]".equals(selftext.trim()))
                ? ""
                : selftext;

        String combined = safeBody.isBlank() ? safeTitle : safeTitle + "\n\n" + safeBody;
        if (combined.isBlank()) {
            return null;
        }

        String cleaned = MARKDOWN_LINK.matcher(combined).replaceAll("$1");
        cleaned = BARE_URL.matcher(cleaned).replaceAll("");
        cleaned = REDDIT_MENTION.matcher(cleaned).replaceAll("");
        cleaned = BLOCKQUOTE.matcher(cleaned).replaceAll("");
        cleaned = HEADING.matcher(cleaned).replaceAll("");
        cleaned = EMPHASIS.matcher(cleaned).replaceAll("$1");
        cleaned = WHITESPACE.matcher(cleaned).replaceAll(" ").trim();

        if (cleaned.length() < MIN_LENGTH) {
            return null;
        }
        if (cleaned.length() > MAX_LENGTH) {
            cleaned = cleaned.substring(0, MAX_LENGTH);
        }
        return cleaned;
    }
}
