package com.investmanager.api.valuation;
import java.time.LocalDate;
public interface TreasuryQuoteProvider {
    MarketQuote quote(ValuationTerms.Instrument instrument, LocalDate maturity, LocalDate date);
}
