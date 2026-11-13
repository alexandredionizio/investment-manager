package com.investmanager.api.shared.exception;

import com.investmanager.api.asset.exception.AssetNotFoundException;
import com.investmanager.api.auth.exception.InvalidCredentialsException;
import com.investmanager.api.broker.exception.BrokerNotFoundException;
import com.investmanager.api.income.exception.IncomeNotFoundException;
import com.investmanager.api.income.exception.IncomeEligibilityConflictException;
import com.investmanager.api.income.exception.InvalidIncomeDatesException;
import com.investmanager.api.income.exception.NoPositionOnBaseDateException;
import com.investmanager.api.portfolio.exception.PortfolioNotFoundException;
import com.investmanager.api.position.exception.InsufficientPositionException;
import com.investmanager.api.quote.exception.QuoteNotFoundException;
import com.investmanager.api.quote.exception.UnsupportedAssetQuoteException;
import com.investmanager.api.portfoliohistory.returnrate.HistoricalQuoteUnavailableException;
import com.investmanager.api.transaction.exception.TransactionNotFoundException;
import com.investmanager.api.transaction.exception.TransactionConcurrencyException;
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
    @ExceptionHandler(com.investmanager.api.asset.discovery.InstrumentException.class)
    public org.springframework.http.ResponseEntity<ErrorResponse> handleInstrument(com.investmanager.api.asset.discovery.InstrumentException exception) {
        return org.springframework.http.ResponseEntity.status(exception.status()).body(new ErrorResponse(exception.status(), exception.code(), exception.getMessage()));
    }


    @ExceptionHandler(com.investmanager.api.benchmark.exception.BenchmarkHistoryIncompleteException.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public ErrorResponse handleBenchmarkHistoryIncomplete(
            com.investmanager.api.benchmark.exception.BenchmarkHistoryIncompleteException exception) {
        return new ErrorResponse(503, "Histórico de benchmark incompleto", exception.getMessage());
    }

    @ExceptionHandler(com.investmanager.api.shared.integration.ExternalIntegrationException.class)
    public org.springframework.http.ResponseEntity<ErrorResponse> handleExternalIntegration(
            com.investmanager.api.shared.integration.ExternalIntegrationException exception) {
        return org.springframework.http.ResponseEntity.status(exception.status()).body(
                new ErrorResponse(exception.status(), "Falha na integração externa", exception.getMessage()));
    }

    @ExceptionHandler(IncomeEligibilityConflictException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorResponse handleIncomeEligibilityConflict(IncomeEligibilityConflictException exception) {
        return new ErrorResponse(409, "Conflito com provento registrado", exception.getMessage());
    }

    @ExceptionHandler(InvalidIncomeDatesException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleInvalidIncomeDates(InvalidIncomeDatesException exception) {
        return new ErrorResponse(400, "Requisição inválida", exception.getMessage());
    }

    @ExceptionHandler(TransactionConcurrencyException.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public ErrorResponse handleTransactionConcurrencyException(TransactionConcurrencyException exception) {
        return new ErrorResponse(
                HttpStatus.SERVICE_UNAVAILABLE.value(),
                "Serviço temporariamente indisponível",
                exception.getMessage()
        );
    }

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
                "Ativo não encontrado."
        );
    }

    @ExceptionHandler(PortfolioNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorResponse handlePortfolioNotFoundException(
            PortfolioNotFoundException exception) {

        return new ErrorResponse(
                HttpStatus.NOT_FOUND.value(),
                "Não encontrado",
                "Carteira não encontrada."
        );
    }

    @ExceptionHandler(TransactionNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorResponse handleTransactionNotFoundException(
            TransactionNotFoundException exception) {

        return new ErrorResponse(
                HttpStatus.NOT_FOUND.value(),
                "Não encontrado",
                "Transação não encontrada."
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
                "Corretora não encontrada."
        );
    }

    @ExceptionHandler(IncomeNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorResponse handleIncomeNotFoundException(
            IncomeNotFoundException exception) {

        return new ErrorResponse(
                HttpStatus.NOT_FOUND.value(),
                "Não encontrado",
                "Provento não encontrado."
        );
    }

    @ExceptionHandler(UnsupportedAssetQuoteException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorResponse handleUnsupportedAssetQuote(UnsupportedAssetQuoteException exception) {
        return new ErrorResponse(404, "Cotação não suportada", exception.getMessage());
    }

    @ExceptionHandler({QuoteNotFoundException.class, HistoricalQuoteUnavailableException.class})
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorResponse handleQuoteNotFoundException(
            RuntimeException exception) {

        return new ErrorResponse(
                HttpStatus.NOT_FOUND.value(),
                "Cotação indisponível",
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
