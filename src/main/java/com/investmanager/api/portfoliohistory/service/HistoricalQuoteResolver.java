package com.investmanager.api.portfoliohistory.service;

import com.investmanager.api.quote.model.HistoricalQuote;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

@Component
public class HistoricalQuoteResolver {

    public BigDecimal resolveClosePrice(
            List<HistoricalQuote> quotes,
            LocalDate date) {

        if (quotes == null) {
            throw new IllegalArgumentException(
                    "Lista de cotações é obrigatória"
            );
        }

        if (date == null) {
            throw new IllegalArgumentException(
                    "Data é obrigatória"
            );
        }

        return quotes.stream()
                .filter(quote ->
                        !quote.date().isAfter(date)
                )
                .max(
                        Comparator.comparing(
                                HistoricalQuote::date
                        )
                )
                .map(HistoricalQuote::closePrice)
                .orElse(null);
    }
}