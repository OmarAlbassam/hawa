package com.hawa.hawa_backend.reddit;

import java.util.List;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public final class RedditQueryBuilder {

    private static final int MAX_QUERY_CHARS = 500;

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
            String group = buildFieldGroup(raw.trim());
            String next = q.length() == 0 ? group : " OR " + group;
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
            log.warn("Reddit query budget exceeded; dropped {} keyword(s) of {}",
                    dropped, selectedKeywords.size());
        }
        return q.toString();
    }

    private static String buildFieldGroup(String term) {
        String quoted = quote(term);
        return "(title:" + quoted + " OR selftext:" + quoted + ")";
    }

    private static String quote(String term) {
        return "\"" + term.replace("\"", "\\\"") + "\"";
    }
}
