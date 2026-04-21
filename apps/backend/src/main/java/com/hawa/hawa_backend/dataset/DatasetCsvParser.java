package com.hawa.hawa_backend.dataset;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
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

        CSVFormat format = CSVFormat.DEFAULT.builder()
                .setHeader()
                .setSkipHeaderRecord(true)
                .setIgnoreEmptyLines(true)
                .setTrim(true)
                .build();

        List<ParsedPost> parsed = new ArrayList<>();
        try (Reader reader = new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8);
             CSVParser csvParser = CSVParser.parse(reader, format)) {

            List<String> headers = normalizeHeaders(csvParser.getHeaderNames());
            validateHeaders(headers, errors);
            if (!errors.isEmpty()) {
                throw new DatasetValidationException(errors);
            }

            int maxRows = properties.maxRows();
            int rowCount = 0;

            for (CSVRecord record : csvParser) {
                rowCount++;
                if (rowCount > maxRows) {
                    errors.add(new DatasetValidationError(
                            "TOO_MANY_ROWS",
                            null,
                            "File has more than " + maxRows + " rows (max allowed)"));
                    break;
                }

                String text = readColumn(record, COL_TEXT);
                String url = readColumn(record, COL_URL);
                String languageRaw = readColumn(record, COL_LANGUAGE);

                if (text == null || text.isBlank()) {
                    errors.add(new DatasetValidationError(
                            "EMPTY_ROW",
                            COL_TEXT,
                            "Row " + rowCount + " has empty text"));
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
                        "NO_DATA_ROWS",
                        null,
                        "File has no data rows"));
            }
        } catch (IOException ex) {
            errors.add(new DatasetValidationError(
                    "MALFORMED_CSV",
                    null,
                    "Failed to read CSV: " + ex.getMessage()));
            throw new DatasetValidationException(errors);
        } catch (IllegalArgumentException ex) {
            errors.add(new DatasetValidationError(
                    "MALFORMED_CSV",
                    null,
                    "Malformed CSV: " + ex.getMessage()));
            throw new DatasetValidationException(errors);
        }

        if (!errors.isEmpty()) {
            throw new DatasetValidationException(errors);
        }
        return parsed;
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

    private static String readColumn(CSVRecord record, String name) {
        if (!record.isMapped(name)) {
            return null;
        }
        String v = record.get(name);
        return v == null ? null : v.trim();
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
