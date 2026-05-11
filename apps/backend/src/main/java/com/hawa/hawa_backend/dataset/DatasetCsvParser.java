package com.hawa.hawa_backend.dataset;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import com.hawa.hawa_backend.dataset.dto.DatasetValidationError;
import com.hawa.hawa_backend.dataset.dto.ParsedPost;
import com.hawa.hawa_backend.enums.LanguageEnum;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class DatasetCsvParser {

    static final String COL_TEXT = "text";
    static final String COL_URL = "url";
    static final String COL_LANGUAGE = "language";
    private static final Set<String> ALLOWED_COLUMNS = Set.of(COL_TEXT, COL_URL, COL_LANGUAGE);

    private final DatasetProperties properties;

    public List<ParsedPost> parse(MultipartFile file) {
        List<DatasetValidationError> errors = new ArrayList<>();

        if (file == null || file.isEmpty()) {
            errors.add(new DatasetValidationError(
                    "EMPTY_FILE", null, "Uploaded file is empty"));
            throw new DatasetValidationException(errors);
        }

        String filename = file.getOriginalFilename();
        String contentType = file.getContentType();
        boolean hasCsvExtension = filename != null
                && filename.toLowerCase(Locale.ROOT).endsWith(".csv");
        boolean hasCsvMimeType = contentType != null
                && (contentType.equalsIgnoreCase("text/csv")
                        || contentType.equalsIgnoreCase("application/csv")
                        || contentType.equalsIgnoreCase("application/vnd.ms-excel"));
        if (!hasCsvExtension && !hasCsvMimeType) {
            errors.add(new DatasetValidationError(
                    "UNSUPPORTED_FORMAT",
                    null,
                    "Unsupported file type. Please upload a CSV file"));
            throw new DatasetValidationException(errors);
        }

        CSVFormat format = baseFormat().build();

        try (Reader reader = new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8)) {
            return parseFromReader(reader, format, ColumnMapping.fixed(), "File", errors);
        } catch (IOException ex) {
            errors.add(new DatasetValidationError(
                    "MALFORMED_CSV",
                    null,
                    "Failed to read CSV: " + ex.getMessage()));
            throw new DatasetValidationException(errors);
        }
    }

    public List<ParsedPost> parsePasted(
            String rawText, String textColumn, String urlColumn, String languageColumn) {
        List<DatasetValidationError> errors = new ArrayList<>();

        if (rawText == null || rawText.isBlank()) {
            errors.add(new DatasetValidationError("EMPTY_FILE", null, "Pasted content is empty"));
            throw new DatasetValidationException(errors);
        }

        char delimiter = detectDelimiter(rawText);
        CSVFormat format = baseFormat().setDelimiter(delimiter).build();

        ColumnMapping mapping = ColumnMapping.mapped(textColumn, urlColumn, languageColumn);

        try (Reader reader = new StringReader(rawText)) {
            return parseFromReader(reader, format, mapping, "Pasted data", errors);
        } catch (IOException ex) {
            errors.add(new DatasetValidationError(
                    "MALFORMED_CSV", null, "Failed to parse pasted content: " + ex.getMessage()));
            throw new DatasetValidationException(errors);
        }
    }

    private List<ParsedPost> parseFromReader(
            Reader reader,
            CSVFormat format,
            ColumnMapping mapping,
            String sourceLabel,
            List<DatasetValidationError> errors) {
        List<ParsedPost> parsed = new ArrayList<>();
        try (CSVParser csvParser = CSVParser.parse(reader, format)) {
            List<String> headers = normalizeHeaders(csvParser.getHeaderNames());

            if (mapping.isFixed()) {
                validateHeaders(headers, errors);
            } else {
                validateMappedHeaders(headers, mapping, errors);
            }
            if (!errors.isEmpty()) {
                throw new DatasetValidationException(errors);
            }

            int maxRows = properties.maxRows();
            int rowCount = 0;

            for (CSVRecord record : csvParser) {
                rowCount++;
                if (rowCount > maxRows) {
                    errors.add(new DatasetValidationError(
                            "TOO_MANY_ROWS", null,
                            sourceLabel + " has more than " + maxRows + " rows (max allowed)"));
                    break;
                }

                String text = readMappedColumn(record, mapping.textHeader());
                String url = mapping.urlHeader() != null ? readMappedColumn(record, mapping.urlHeader()) : null;
                String languageRaw = mapping.languageHeader() != null
                        ? readMappedColumn(record, mapping.languageHeader())
                        : null;

                if (text == null || text.isBlank()) {
                    errors.add(new DatasetValidationError(
                            "EMPTY_ROW", COL_TEXT, "Row " + rowCount + " has empty text"));
                    continue;
                }

                LanguageEnum language = resolveLanguage(languageRaw, rowCount, errors);
                parsed.add(new ParsedPost(
                        text,
                        (url == null || url.isBlank()) ? null : url,
                        language));
            }

            if (rowCount == 0) {
                errors.add(new DatasetValidationError(
                        "NO_DATA_ROWS", null, sourceLabel + " has no data rows"));
            }
        } catch (IOException ex) {
            errors.add(new DatasetValidationError(
                    "MALFORMED_CSV", null, "Failed to read CSV: " + ex.getMessage()));
            throw new DatasetValidationException(errors);
        } catch (IllegalArgumentException ex) {
            errors.add(new DatasetValidationError(
                    "MALFORMED_CSV", null, "Malformed CSV: " + ex.getMessage()));
            throw new DatasetValidationException(errors);
        }

        if (!errors.isEmpty()) {
            throw new DatasetValidationException(errors);
        }
        return parsed;
    }

    private static CSVFormat.Builder baseFormat() {
        return CSVFormat.DEFAULT.builder()
                .setHeader()
                .setSkipHeaderRecord(true)
                .setIgnoreEmptyLines(true)
                .setTrim(true)
                .setIgnoreHeaderCase(true);
    }

    /**
     * Detects ',' vs '\t' delimiter from the first line. Must stay in sync with
     * the frontend heuristic in {@code apps/frontend/src/pages/Analysis/StartAnalysis.tsx}
     * so the client preview matches what this parser produces.
     */
    static char detectDelimiter(String text) {
        String firstLine = text.indexOf('\n') >= 0
                ? text.substring(0, text.indexOf('\n'))
                : text;
        long tabs = firstLine.chars().filter(c -> c == '\t').count();
        long commas = firstLine.chars().filter(c -> c == ',').count();
        return tabs >= commas ? '\t' : ',';
    }

    private record ColumnMapping(String textHeader, String urlHeader, String languageHeader,
                                 String rawText, String rawUrl, String rawLang) {
        static ColumnMapping fixed() {
            return new ColumnMapping(COL_TEXT, COL_URL, COL_LANGUAGE, COL_TEXT, COL_URL, COL_LANGUAGE);
        }

        static ColumnMapping mapped(String textColumn, String urlColumn, String languageColumn) {
            String t = textColumn.trim().toLowerCase(Locale.ROOT);
            String u = urlColumn != null ? urlColumn.trim().toLowerCase(Locale.ROOT) : null;
            String l = languageColumn != null ? languageColumn.trim().toLowerCase(Locale.ROOT) : null;
            return new ColumnMapping(t, u, l, textColumn, urlColumn, languageColumn);
        }

        boolean isFixed() {
            return COL_TEXT.equals(textHeader) && COL_URL.equals(urlHeader) && COL_LANGUAGE.equals(languageHeader)
                    && COL_TEXT.equals(rawText);
        }
    }

    private static void validateMappedHeaders(
            List<String> headers, ColumnMapping mapping, List<DatasetValidationError> errors) {
        if (!headers.contains(mapping.textHeader())) {
            errors.add(new DatasetValidationError(
                    "MISSING_COLUMN", COL_TEXT,
                    "Column '" + mapping.rawText() + "' not found in pasted data"));
        }
        if (mapping.urlHeader() != null && !headers.contains(mapping.urlHeader())) {
            errors.add(new DatasetValidationError(
                    "MISSING_COLUMN", COL_URL,
                    "Column '" + mapping.rawUrl() + "' not found in pasted data"));
        }
        if (mapping.languageHeader() != null && !headers.contains(mapping.languageHeader())) {
            errors.add(new DatasetValidationError(
                    "MISSING_COLUMN", COL_LANGUAGE,
                    "Column '" + mapping.rawLang() + "' not found in pasted data"));
        }
    }

    private static String readMappedColumn(CSVRecord record, String normalizedHeader) {
        if (!record.isMapped(normalizedHeader)) {
            return null;
        }
        String v = record.get(normalizedHeader);
        return v == null ? null : v.trim();
    }

    private static List<String> normalizeHeaders(List<String> raw) {
        List<String> out = new ArrayList<>(raw.size());
        for (String h : raw) {
            out.add(h == null ? "" : h.trim().toLowerCase(Locale.ROOT));
        }
        return out;
    }

    private static void validateHeaders(List<String> headers, List<DatasetValidationError> errors) {
        if (!headers.contains(COL_TEXT)) {
            errors.add(new DatasetValidationError(
                    "MISSING_COLUMN",
                    COL_TEXT,
                    "Required column 'text' is missing"));
        }
        Set<String> seen = new HashSet<>();
        for (String h : headers) {
            if (h.isEmpty()) {
                continue;
            }
            if (!ALLOWED_COLUMNS.contains(h)) {
                errors.add(new DatasetValidationError(
                        "UNKNOWN_COLUMN",
                        h,
                        "Unknown column '" + h + "'. Allowed: " + String.join(", ", ALLOWED_COLUMNS)));
            }
            if (!seen.add(h)) {
                errors.add(new DatasetValidationError(
                        "DUPLICATE_COLUMN",
                        h,
                        "Duplicate column '" + h + "'"));
            }
        }
    }

    private static LanguageEnum resolveLanguage(
            String raw, int rowNumber, List<DatasetValidationError> errors) {
        if (raw == null || raw.isBlank()) {
            return LanguageEnum.EN;
        }
        String upper = raw.trim().toUpperCase(Locale.ROOT);
        try {
            return LanguageEnum.valueOf(upper);
        } catch (IllegalArgumentException ex) {
            errors.add(new DatasetValidationError(
                    "INVALID_LANGUAGE",
                    COL_LANGUAGE,
                    "Row " + rowNumber + " has invalid language '" + raw
                            + "'. Allowed: " + Arrays.toString(LanguageEnum.values())));
            return LanguageEnum.EN;
        }
    }
}
