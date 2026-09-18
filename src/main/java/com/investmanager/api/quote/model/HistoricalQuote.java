package com.investmanager.api.quote.model;

import java.math.BigDecimal;
import java.time.LocalDate;

public record HistoricalQuote(
        LocalDate date,
        BigDecimal closePrice
) {
}