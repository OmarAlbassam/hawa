package com.hawa.hawa_backend.dataset.dto;

import java.time.LocalDateTime;
import java.util.List;

public record DatasetValidationErrorResponse(
        int status,
        String message,
        LocalDateTime timestamp,
        List<DatasetValidationError> errors
) {}
