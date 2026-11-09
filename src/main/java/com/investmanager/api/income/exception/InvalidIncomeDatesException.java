package com.investmanager.api.income.exception;

public class InvalidIncomeDatesException extends RuntimeException {
    public InvalidIncomeDatesException() {
        super("A data de pagamento não pode ser anterior à data-base do provento.");
    }
}
