package com.investmanager.api.shared.exception;

public record ErrorResponse(
        int status,
        String error,
        String message
) {
}