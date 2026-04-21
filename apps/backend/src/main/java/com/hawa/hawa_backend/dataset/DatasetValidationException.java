package com.hawa.hawa_backend.dataset;

import java.util.List;

import com.hawa.hawa_backend.dataset.dto.DatasetValidationError;

public class DatasetValidationException extends RuntimeException {

    private final List<DatasetValidationError> errors;

    public DatasetValidationException(List<DatasetValidationError> errors) {
        super("Dataset validation failed");
        this.errors = List.copyOf(errors);
    }

    public List<DatasetValidationError> getErrors() {
        return errors;
    }
}
