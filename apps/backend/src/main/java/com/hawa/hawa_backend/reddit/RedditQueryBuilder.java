package com.hawa.hawa_backend.reddit;

import java.util.List;
import java.util.regex.Pattern;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public final class RedditQueryBuilder {

    private static final int MAX_QUERY_CHARS = 500;
    private static final Pattern NEEDS_QUOTING = Pattern.compile("[\\s\"'()\\-:/\\\\]");

    private RedditQueryBuilder() {
    }

    public static String build(List<String> selectedKeywords) {
        if (selectedKeywords == null || selectedKeywords.isEmpty()) {
            throw new IllegalArgumentException("At least one keyword is required to build a Reddit query");
        }
        StringBuilder q = new StringBuilder();
        int dropped = 0;
        for (String raw : selectedKeywords) {
            if (raw == null || raw.isBlank()) {
                continue;
            }
            String term = formatTerm(raw.trim());
            String next = q.length() == 0 ? term : " OR " + term;
            if (q.length() + next.length() > MAX_QUERY_CHARS) {
                dropped++;
                continue;
            }
            q.append(next);
        }
        if (q.length() == 0) {
            throw new IllegalArgumentException("No usable keyword terms after trimming");
        }
        if (dropped > 0) {
            log.debug("Reddit query budget exceeded; dropped {} keyword(s)", dropped);
        }
        return q.toString();
    }

    private static String formatTerm(String term) {
        return NEEDS_QUOTING.matcher(term).find() ? quote(term) : term;
    }

    private static String quote(String term) {
        return "\"" + term.replace("\"", "\\\"") + "\"";
    }
}
