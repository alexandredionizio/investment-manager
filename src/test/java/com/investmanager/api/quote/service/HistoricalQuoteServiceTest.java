package com.investmanager.api.quote.service;

import com.investmanager.api.portfoliohistory.model.PortfolioHistoryGranularity;
import com.investmanager.api.portfoliohistory.model.PortfolioHistoryPeriod;
import com.investmanager.api.quote.client.BrapiClient;
import com.investmanager.api.quote.client.dto.BrapiHistoricalData;
import com.investmanager.api.quote.client.dto.BrapiHistoricalPrice;
import com.investmanager.api.quote.client.dto.BrapiHistoricalResponse;
import com.investmanager.api.quote.client.dto.BrapiHistoricalResult;
import com.investmanager.api.quote.client.mapper.BrapiHistoricalQuoteMapper;
import com.investmanager.api.quote.client.mapper.BrapiHistoricalRangeMapper;
import com.investmanager.api.quote.exception.QuoteNotFoundException;
import com.investmanager.api.quote.model.HistoricalQuote;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HistoricalQuoteServiceTest {

    @Mock
    private BrapiClient brapiClient;

    private HistoricalQuoteService historicalQuoteService;

    @BeforeEach
    void setUp() {

        BrapiHistoricalQuoteMapper historicalQuoteMapper =
                new BrapiHistoricalQuoteMapper();

        BrapiHistoricalRangeMapper historicalRangeMapper =
                new BrapiHistoricalRangeMapper();

        historicalQuoteService =
                new HistoricalQuoteService(
                        brapiClient,
                        historicalQuoteMapper,
                        historicalRangeMapper
                );
    }

    @Test
    void shouldReturnHistoricalQuotes() {

        BrapiHistoricalPrice price =
                createHistoricalPrice();

        BrapiHistoricalResponse response =
                createHistoricalResponse(
                        "1d",
                        "1y",
                        price
                );

        when(brapiClient.getHistoricalQuotes(
                "ITUB4",
                "1y",
                "1d"
        )).thenReturn(response);

        List<HistoricalQuote> quotes =
                historicalQuoteService.getHistoricalQuotes(
                        "ITUB4",
                        PortfolioHistoryPeriod.ONE_YEAR,
                        PortfolioHistoryGranularity.WEEKLY
                );

        assertEquals(1, quotes.size());

        assertEquals(
                LocalDate.of(2026, 9, 18),
                quotes.getFirst().date()
        );

        assertEquals(
                new BigDecimal("42.26"),
                quotes.getFirst().closePrice()
        );

        verify(brapiClient).getHistoricalQuotes(
                "ITUB4",
                "1y",
                "1d"
        );
    }

    @Test
    void shouldReturnHistoricalQuotesForCustomPeriod() {

        LocalDate startDate =
                LocalDate.of(2026, 9, 1);

        LocalDate endDate =
                LocalDate.of(2026, 9, 18);

        BrapiHistoricalPrice price =
                createHistoricalPrice();

        BrapiHistoricalResponse response =
                createHistoricalResponse(
                        "1d",
                        "1mo",
                        price
                );

        when(brapiClient.getHistoricalQuotes(
                "ITUB4",
                startDate,
                endDate,
                "1d"
        )).thenReturn(response);

        List<HistoricalQuote> quotes =
                historicalQuoteService.getHistoricalQuotes(
                        "ITUB4",
                        startDate,
                        endDate,
                        PortfolioHistoryGranularity.DAILY
                );

        assertEquals(1, quotes.size());

        assertEquals(
                LocalDate.of(2026, 9, 18),
                quotes.getFirst().date()
        );

        assertEquals(
                new BigDecimal("42.26"),
                quotes.getFirst().closePrice()
        );

        verify(brapiClient).getHistoricalQuotes(
                "ITUB4",
                startDate,
                endDate,
                "1d"
        );
    }

    @Test
    void shouldThrowExceptionWhenHistoricalResponseIsEmpty() {

        BrapiHistoricalResponse response =
                new BrapiHistoricalResponse(
                        List.of(),
                        "2026-09-18T15:45:12.189Z",
                        0L
                );

        when(brapiClient.getHistoricalQuotes(
                "ABCXYZ",
                "1mo",
                "1d"
        )).thenReturn(response);

        assertThrows(
                QuoteNotFoundException.class,
                () -> historicalQuoteService.getHistoricalQuotes(
                        "ABCXYZ",
                        PortfolioHistoryPeriod.ONE_MONTH,
                        PortfolioHistoryGranularity.DAILY
                )
        );

        verify(brapiClient).getHistoricalQuotes(
                "ABCXYZ",
                "1mo",
                "1d"
        );
    }

    @Test
    void shouldThrowExceptionWhenCustomPeriodUsesPredefinedMethod() {

        assertThrows(
                IllegalArgumentException.class,
                () -> historicalQuoteService.getHistoricalQuotes(
                        "ITUB4",
                        PortfolioHistoryPeriod.CUSTOM,
                        PortfolioHistoryGranularity.DAILY
                )
        );
    }

    @Test
    void shouldThrowExceptionWhenCustomDatesAreMissing() {

        assertThrows(
                IllegalArgumentException.class,
                () -> historicalQuoteService.getHistoricalQuotes(
                        "ITUB4",
                        null,
                        null,
                        PortfolioHistoryGranularity.DAILY
                )
        );
    }

    @Test
    void shouldThrowExceptionWhenCustomEndDateIsBeforeStartDate() {

        LocalDate startDate =
                LocalDate.of(2026, 9, 18);

        LocalDate endDate =
                LocalDate.of(2026, 9, 17);

        assertThrows(
                IllegalArgumentException.class,
                () -> historicalQuoteService.getHistoricalQuotes(
                        "ITUB4",
                        startDate,
                        endDate,
                        PortfolioHistoryGranularity.DAILY
                )
        );
    }

    private BrapiHistoricalPrice createHistoricalPrice() {

        return new BrapiHistoricalPrice(
                1789700400L,
                new BigDecimal("42.40"),
                new BigDecimal("42.60"),
                new BigDecimal("41.98"),
                new BigDecimal("42.26"),
                5267500L,
                new BigDecimal("42.26")
        );
    }

    private BrapiHistoricalResponse createHistoricalResponse(
            String interval,
            String range,
            BrapiHistoricalPrice price) {

        BrapiHistoricalData data =
                new BrapiHistoricalData(
                        interval,
                        range,
                        List.of(price)
                );

        BrapiHistoricalResult result =
                new BrapiHistoricalResult(
                        "ITUB4",
                        "ITUB4",
                        false,
                        data
                );

        return new BrapiHistoricalResponse(
                List.of(result),
                "2026-09-18T15:45:12.189Z",
                0L
        );
    }
}