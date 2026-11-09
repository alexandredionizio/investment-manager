package com.investmanager.api.quote.exception;

public class UnsupportedAssetQuoteException extends RuntimeException {
    public UnsupportedAssetQuoteException(String symbol, String reason) {
        super("Cotação e avaliação de mercado indisponíveis para " + symbol + ": " + reason);
    }
}
