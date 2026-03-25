package com.hawa.hawa_backend.exception;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ApiError {

    private int status;
    private String message;
    private LocalDateTime timestamp;
}
