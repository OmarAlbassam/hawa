package com.hawa.hawa_backend.reddit;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.hawa.hawa_backend.brand.Brand;
import com.hawa.hawa_backend.enums.KeywordTypeEnum;
import com.hawa.hawa_backend.keyword.Keyword;

class RedditQueryBuilderTest {

    @Test
    void shouldQuoteBrand_andOrJoinKeywords() {
        Brand brand = Brand.builder().brandName("Nike").build();
        List<Keyword> keywords = List.of(
                Keyword.builder().keyword("jordan").keywordType(KeywordTypeEnum.PRODUCT).build(),
                Keyword.builder().keyword("air max").keywordType(KeywordTypeEnum.PRODUCT).build());

        String query = RedditQueryBuilder.build(brand, keywords);

        assertThat(query).isEqualTo("\"Nike\" OR jordan OR \"air max\"");
    }

    @Test
    void shouldQuoteBrandNameContainingSpaces() {
        Brand brand = Brand.builder().brandName("Coca Cola").build();
        String query = RedditQueryBuilder.build(brand, List.of());
        assertThat(query).isEqualTo("\"Coca Cola\"");
    }

    @Test
    void shouldDropKeywords_whenExceedingBudget() {
        Brand brand = Brand.builder().brandName("Brand").build();
        List<Keyword> keywords = new ArrayList<>();
        String longTerm = "x".repeat(60);
        for (int i = 0; i < 20; i++) {
            keywords.add(Keyword.builder().keyword(longTerm + i).keywordType(KeywordTypeEnum.PRODUCT).build());
        }

        String query = RedditQueryBuilder.build(brand, keywords);

        assertThat(query.length()).isLessThanOrEqualTo(500);
    }

    @Test
    void shouldIgnoreBlankKeywords() {
        Brand brand = Brand.builder().brandName("Brand").build();
        List<Keyword> keywords = List.of(
                Keyword.builder().keyword("  ").keywordType(KeywordTypeEnum.PRODUCT).build(),
                Keyword.builder().keyword("real").keywordType(KeywordTypeEnum.PRODUCT).build());

        String query = RedditQueryBuilder.build(brand, keywords);

        assertThat(query).isEqualTo("\"Brand\" OR real");
    }
}
