package com.investmanager.api.income.exception;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

public class NoPositionOnBaseDateException extends RuntimeException {

    private static final DateTimeFormatter DATE_FORMATTER =
            DateTimeFormatter.ofPattern("dd/MM/yyyy");

    public NoPositionOnBaseDateException(
            String ticker,
            LocalDate baseDate) {

        super(
                "Não existe posição do ativo "
                        + ticker
                        + " na data-base "
                        + baseDate.format(DATE_FORMATTER)
        );
    }
}