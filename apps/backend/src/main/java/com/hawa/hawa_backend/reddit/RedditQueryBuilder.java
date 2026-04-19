package com.hawa.hawa_backend.reddit;

import java.util.List;
import java.util.regex.Pattern;

import com.hawa.hawa_backend.brand.Brand;
import com.hawa.hawa_backend.keyword.Keyword;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public final class RedditQueryBuilder {

    private static final int MAX_QUERY_CHARS = 500;
    private static final Pattern NEEDS_QUOTING = Pattern.compile("[\\s\"'()\\-:/\\\\]");

    private RedditQueryBuilder() {
    }

    public static String build(Brand brand, List<Keyword> keywords) {
        if (brand == null || brand.getBrandName() == null || brand.getBrandName().isBlank()) {
            throw new IllegalArgumentException("Brand must have a non-blank name");
        }
        StringBuilder q = new StringBuilder(quote(brand.getBrandName()));
        int dropped = 0;
        if (keywords != null) {
            for (Keyword kw : keywords) {
                if (kw == null || kw.getKeyword() == null || kw.getKeyword().isBlank()) {
                    continue;
                }
                String term = formatTerm(kw.getKeyword().trim());
                String next = " OR " + term;
                if (q.length() + next.length() > MAX_QUERY_CHARS) {
                    dropped++;
                    continue;
                }
                q.append(next);
            }
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
