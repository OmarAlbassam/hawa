package com.hawa.hawa_backend.analysis.dto;

import java.time.LocalDate;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PasteDatasetRequest(
        @NotBlank(message = "rawText must not be blank")
        @Size(max = 5_000_000, message = "Pasted content too large")
        String rawText,

        @NotBlank(message = "textColumn must not be blank")
        @Size(max = 256, message = "textColumn name too long")
        String textColumn,

        @Size(max = 256, message = "urlColumn name too long")
        String urlColumn,

        @Size(max = 256, message = "languageColumn name too long")
        String languageColumn,

        LocalDate dateFrom,
        LocalDate dateTo) {
}
