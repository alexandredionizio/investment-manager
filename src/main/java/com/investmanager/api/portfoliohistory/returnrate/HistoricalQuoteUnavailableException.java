package com.investmanager.api.portfoliohistory.returnrate;

import java.time.LocalDate;

public class HistoricalQuoteUnavailableException extends RuntimeException {

    public HistoricalQuoteUnavailableException(
            String ticker,
            LocalDate date) {

        super(
                "Cotação histórica indisponível para o ativo "
                        + ticker
                        + " na data "
                        + date
                        + " ou em data anterior."
        );
    }
}