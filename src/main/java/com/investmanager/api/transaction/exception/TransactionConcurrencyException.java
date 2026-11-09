package com.investmanager.api.transaction.exception;

public class TransactionConcurrencyException extends RuntimeException {

    public TransactionConcurrencyException(Throwable cause) {
        super("Não foi possível registrar a operação devido à concorrência. Tente novamente.", cause);
    }
}
