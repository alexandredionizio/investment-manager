package com.investmanager.api.quote.exception;

public class QuoteNotFoundException extends RuntimeException {

    public QuoteNotFoundException(String symbol) {
        super("Cotação não encontrada para o ativo: " + symbol);
    }
}
