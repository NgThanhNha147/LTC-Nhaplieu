package com.example.dynamicform.common;

import java.time.Instant;
import java.util.List;

public record ApiError(
        Instant timestamp,
        int status,
        String code,
        String message,
        List<FieldErrorItem> fieldErrors
) {
    public record FieldErrorItem(String field, String code, String message) {}
}
