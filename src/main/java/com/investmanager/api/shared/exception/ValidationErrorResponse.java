package com.investmanager.api.shared.exception;

import java.util.Map;

public record ValidationErrorResponse(
        int status,
        String error,
        Map<String, String> fields
) {
}
