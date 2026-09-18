package com.investmanager.api.quote.client.mapper;

import com.investmanager.api.quote.client.dto.BrapiHistoricalPrice;
import com.investmanager.api.quote.client.mapper.BrapiHistoricalQuoteMapper;
import com.investmanager.api.quote.model.HistoricalQuote;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BrapiHistoricalQuoteMapperTest {

    private final BrapiHistoricalQuoteMapper mapper =
            new BrapiHistoricalQuoteMapper();

    @Test
    void shouldConvertBrapiHistoricalPriceToHistoricalQuote() {

        BrapiHistoricalPrice historicalPrice =
                new BrapiHistoricalPrice(
                        1789700400L,
                        new BigDecimal("42.40"),
                        new BigDecimal("42.60"),
                        new BigDecimal("41.98"),
                        new BigDecimal("42.26"),
                        5267500L,
                        new BigDecimal("42.26")
                );

        HistoricalQuote historicalQuote =
                mapper.toHistoricalQuote(historicalPrice);

        assertEquals(
                LocalDate.of(2026, 9, 18),
                historicalQuote.date()
        );

        assertEquals(
                new BigDecimal("42.26"),
                historicalQuote.closePrice()
        );
    }
}