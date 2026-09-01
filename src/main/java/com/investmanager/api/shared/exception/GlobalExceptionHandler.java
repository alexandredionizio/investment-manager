package com.investmanager.api.shared.exception;

import com.investmanager.api.asset.exception.AssetNotFoundException;
import com.investmanager.api.portfolio.exception.PortfolioNotFoundException;
import com.investmanager.api.transaction.exception.TransactionNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ValidationErrorResponse handleValidationException (
            MethodArgumentNotValidException exception) {
        Map<String, String> fields = new HashMap<>();

        exception.getBindingResult()
                .getFieldErrors()
                .forEach(error ->
                        fields.put(error.getField(), error.getDefaultMessage())
                );

        return new ValidationErrorResponse(
                HttpStatus.BAD_REQUEST.value(),
                "Erro de validação",
                fields
        );
    }

    @ExceptionHandler(AssetNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Map<String, Object> handleAssetNotFoundException(
            AssetNotFoundException exception) {

        return Map.of(
                "status", HttpStatus.NOT_FOUND.value(),
                "error", "Não encontrado",
                "message", exception.getMessage()
        );
    }

    @ExceptionHandler(PortfolioNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Map<String, Object> handlePortfolioNotFoundException(
            PortfolioNotFoundException exception) {

        return Map.of(
                "status", HttpStatus.NOT_FOUND.value(),
                "error", "Não encontrado",
                "message", exception.getMessage()
        );
    }

    @ExceptionHandler(TransactionNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Map<String, Object> handleTransactionNotFoundException(
            TransactionNotFoundException exception) {

        return Map.of(
                "status", HttpStatus.NOT_FOUND.value(),
                "error", "Não encontrado",
                "message", exception.getMessage()
        );
    }
}
