package com.hawa.hawa_backend.dataset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.hawa.hawa_backend.dataset.dto.DatasetValidationError;
import com.hawa.hawa_backend.dataset.dto.ParsedPost;

class DatasetCsvParserTest {

    private final DatasetCsvParser parser = new DatasetCsvParser(new DatasetProperties(1000));

    @Test
    void parsePastedShouldErrorWhenMappedUrlColumnIsMissing() {
        String raw = "text,language\nhello world,EN\n";

        assertThatThrownBy(() -> parser.parsePasted(raw, "text", "url", null))
                .isInstanceOfSatisfying(DatasetValidationException.class, ex -> {
                    List<DatasetValidationError> errors = ex.getErrors();
                    assertThat(errors)
                            .anyMatch(e -> "MISSING_COLUMN".equals(e.code())
                                    && "url".equals(e.field()));
                });
    }

    @Test
    void parsePastedShouldErrorWhenMappedLanguageColumnIsMissing() {
        String raw = "text,url\nhello,https://example.com\n";

        assertThatThrownBy(() -> parser.parsePasted(raw, "text", null, "language"))
                .isInstanceOfSatisfying(DatasetValidationException.class, ex -> {
                    List<DatasetValidationError> errors = ex.getErrors();
                    assertThat(errors)
                            .anyMatch(e -> "MISSING_COLUMN".equals(e.code())
                                    && "language".equals(e.field()));
                });
    }

    @Test
    void parsePastedShouldSucceedWhenOnlyTextColumnMappedAndPresent() {
        String raw = "text\nhello world\n";

        List<ParsedPost> parsed = parser.parsePasted(raw, "text", null, null);
        assertThat(parsed).hasSize(1);
        assertThat(parsed.get(0).text()).isEqualTo("hello world");
        assertThat(parsed.get(0).url()).isNull();
    }

    @Test
    void parsePastedShouldHonorQuotedFieldsContainingDelimiter() {
        String raw = "text,url\n\"hello, world\",https://example.com\n";

        List<ParsedPost> parsed = parser.parsePasted(raw, "text", "url", null);
        assertThat(parsed).hasSize(1);
        assertThat(parsed.get(0).text()).isEqualTo("hello, world");
        assertThat(parsed.get(0).url()).isEqualTo("https://example.com");
    }
}
