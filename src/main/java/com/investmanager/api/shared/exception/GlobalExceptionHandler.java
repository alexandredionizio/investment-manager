package com.investmanager.api.shared.exception;

import com.investmanager.api.asset.exception.AssetNotFoundException;
import com.investmanager.api.auth.exception.InvalidCredentialsException;
import com.investmanager.api.broker.exception.BrokerNotFoundException;
import com.investmanager.api.income.exception.IncomeNotFoundException;
import com.investmanager.api.income.exception.NoPositionOnBaseDateException;
import com.investmanager.api.portfolio.exception.PortfolioNotFoundException;
import com.investmanager.api.position.exception.InsufficientPositionException;
import com.investmanager.api.quote.exception.QuoteNotFoundException;
import com.investmanager.api.transaction.exception.TransactionNotFoundException;
import com.investmanager.api.user.exception.UserAlreadyExistsException;
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
    public ValidationErrorResponse handleValidationException(
            MethodArgumentNotValidException exception) {

        Map<String, String> fields = new HashMap<>();

        exception.getBindingResult()
                .getFieldErrors()
                .forEach(error ->
                        fields.put(
                                error.getField(),
                                error.getDefaultMessage()
                        )
                );

        return new ValidationErrorResponse(
                HttpStatus.BAD_REQUEST.value(),
                "Erro de validação",
                fields
        );
    }

    @ExceptionHandler(AssetNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorResponse handleAssetNotFoundException(
            AssetNotFoundException exception) {

        return new ErrorResponse(
                HttpStatus.NOT_FOUND.value(),
                "Não encontrado",
                exception.getMessage()
        );
    }

    @ExceptionHandler(PortfolioNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorResponse handlePortfolioNotFoundException(
            PortfolioNotFoundException exception) {

        return new ErrorResponse(
                HttpStatus.NOT_FOUND.value(),
                "Não encontrado",
                exception.getMessage()
        );
    }

    @ExceptionHandler(TransactionNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorResponse handleTransactionNotFoundException(
            TransactionNotFoundException exception) {

        return new ErrorResponse(
                HttpStatus.NOT_FOUND.value(),
                "Não encontrado",
                exception.getMessage()
        );
    }

    @ExceptionHandler(InsufficientPositionException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleInsufficientPositionException(
            InsufficientPositionException exception) {

        return new ErrorResponse(
                HttpStatus.BAD_REQUEST.value(),
                "Requisição inválida",
                exception.getMessage()
        );
    }

    @ExceptionHandler(NoPositionOnBaseDateException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleNoPositionOnBaseDateException(
            NoPositionOnBaseDateException exception) {

        return new ErrorResponse(
                HttpStatus.BAD_REQUEST.value(),
                "Requisição inválida",
                exception.getMessage()
        );
    }

    @ExceptionHandler(BrokerNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorResponse handleBrokerNotFoundException(
            BrokerNotFoundException exception) {

        return new ErrorResponse(
                HttpStatus.NOT_FOUND.value(),
                "Não encontrado",
                exception.getMessage()
        );
    }

    @ExceptionHandler(IncomeNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorResponse handleIncomeNotFoundException(
            IncomeNotFoundException exception) {

        return new ErrorResponse(
                HttpStatus.NOT_FOUND.value(),
                "Não encontrado",
                exception.getMessage()
        );
    }

    @ExceptionHandler(QuoteNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorResponse handleQuoteNotFoundException(
            QuoteNotFoundException exception) {

        return new ErrorResponse(
                HttpStatus.NOT_FOUND.value(),
                "Não encontrado",
                exception.getMessage()
        );
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public ErrorResponse handleInvalidCredentialsException(
            InvalidCredentialsException exception) {

        return new ErrorResponse(
                HttpStatus.UNAUTHORIZED.value(),
                "Não autorizado",
                exception.getMessage()
        );
    }

    @ExceptionHandler(UserAlreadyExistsException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorResponse handleUserAlreadyExistsException(
            UserAlreadyExistsException exception) {

        return new ErrorResponse(
                HttpStatus.CONFLICT.value(),
                "Conflito",
                exception.getMessage()
        );
    }
}