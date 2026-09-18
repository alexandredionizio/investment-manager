package com.investmanager.api.portfoliohistory.service;

import com.investmanager.api.quote.model.HistoricalQuote;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class HistoricalQuoteResolverTest {

    private HistoricalQuoteResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new HistoricalQuoteResolver();
    }

    @Test
    void shouldReturnQuoteForExactDate() {

        List<HistoricalQuote> quotes =
                createQuotes();

        BigDecimal price =
                resolver.resolveClosePrice(
                        quotes,
                        LocalDate.of(2026, 9, 18)
                );

        assertEquals(
                0,
                new BigDecimal("42.26")
                        .compareTo(price)
        );
    }

    @Test
    void shouldReturnLastAvailableQuoteBeforeDate() {

        List<HistoricalQuote> quotes =
                createQuotes();

        BigDecimal price =
                resolver.resolveClosePrice(
                        quotes,
                        LocalDate.of(2026, 9, 20)
                );

        assertEquals(
                0,
                new BigDecimal("42.26")
                        .compareTo(price)
        );
    }

    @Test
    void shouldReturnMostRecentPreviousQuote() {

        List<HistoricalQuote> quotes =
                createQuotes();

        BigDecimal price =
                resolver.resolveClosePrice(
                        quotes,
                        LocalDate.of(2026, 9, 17)
                );

        assertEquals(
                0,
                new BigDecimal("42.00")
                        .compareTo(price)
        );
    }

    @Test
    void shouldReturnNullWhenThereIsNoPreviousQuote() {

        List<HistoricalQuote> quotes =
                createQuotes();

        BigDecimal price =
                resolver.resolveClosePrice(
                        quotes,
                        LocalDate.of(2026, 9, 14)
                );

        assertNull(price);
    }

    @Test
    void shouldReturnNullWhenQuoteListIsEmpty() {

        BigDecimal price =
                resolver.resolveClosePrice(
                        List.of(),
                        LocalDate.of(2026, 9, 18)
                );

        assertNull(price);
    }

    @Test
    void shouldWorkWhenQuotesAreNotOrdered() {

        List<HistoricalQuote> quotes =
                List.of(
                        new HistoricalQuote(
                                LocalDate.of(2026, 9, 18),
                                new BigDecimal("42.26")
                        ),
                        new HistoricalQuote(
                                LocalDate.of(2026, 9, 15),
                                new BigDecimal("40.00")
                        ),
                        new HistoricalQuote(
                                LocalDate.of(2026, 9, 17),
                                new BigDecimal("42.00")
                        ),
                        new HistoricalQuote(
                                LocalDate.of(2026, 9, 16),
                                new BigDecimal("41.00")
                        )
                );

        BigDecimal price =
                resolver.resolveClosePrice(
                        quotes,
                        LocalDate.of(2026, 9, 17)
                );

        assertEquals(
                0,
                new BigDecimal("42.00")
                        .compareTo(price)
        );
    }

    @Test
    void shouldThrowExceptionWhenQuotesAreNull() {

        assertThrows(
                IllegalArgumentException.class,
                () -> resolver.resolveClosePrice(
                        null,
                        LocalDate.of(2026, 9, 18)
                )
        );
    }

    @Test
    void shouldThrowExceptionWhenDateIsNull() {

        assertThrows(
                IllegalArgumentException.class,
                () -> resolver.resolveClosePrice(
                        List.of(),
                        null
                )
        );
    }

    private List<HistoricalQuote> createQuotes() {

        return List.of(
                new HistoricalQuote(
                        LocalDate.of(2026, 9, 15),
                        new BigDecimal("40.00")
                ),
                new HistoricalQuote(
                        LocalDate.of(2026, 9, 16),
                        new BigDecimal("41.00")
                ),
                new HistoricalQuote(
                        LocalDate.of(2026, 9, 17),
                        new BigDecimal("42.00")
                ),
                new HistoricalQuote(
                        LocalDate.of(2026, 9, 18),
                        new BigDecimal("42.26")
                )
        );
    }
}