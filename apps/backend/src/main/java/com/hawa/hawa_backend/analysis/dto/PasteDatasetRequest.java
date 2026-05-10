package com.hawa.hawa_backend.analysis.dto;

import java.time.LocalDate;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PasteDatasetRequest(
        @NotBlank(message = "rawText must not be blank")
        @Size(max = 5_000_000, message = "Pasted content too large")
        String rawText,

        @NotBlank(message = "textColumn must not be blank")
        String textColumn,

        String urlColumn,
        String languageColumn,

        LocalDate dateFrom,
        LocalDate dateTo) {
}
