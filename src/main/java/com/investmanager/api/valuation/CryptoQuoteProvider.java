package com.investmanager.api.valuation;

public interface CryptoQuoteProvider {
    MarketQuote quote(SupportedCryptocurrency currency);
}
