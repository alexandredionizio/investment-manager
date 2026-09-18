package com.investmanager.api.quote.client.mapper;

import com.investmanager.api.portfoliohistory.model.PortfolioHistoryPeriod;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BrapiHistoricalRangeMapperTest {

    private BrapiHistoricalRangeMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new BrapiHistoricalRangeMapper();
    }

    @Test
    void shouldMapPortfolioHistoryPeriodsToBrapiRanges() {

        assertEquals(
                "1mo",
                mapper.toRange(PortfolioHistoryPeriod.ONE_MONTH)
        );

        assertEquals(
                "3mo",
                mapper.toRange(PortfolioHistoryPeriod.THREE_MONTHS)
        );

        assertEquals(
                "6mo",
                mapper.toRange(PortfolioHistoryPeriod.SIX_MONTHS)
        );

        assertEquals(
                "1y",
                mapper.toRange(PortfolioHistoryPeriod.ONE_YEAR)
        );

        assertEquals(
                "2y",
                mapper.toRange(PortfolioHistoryPeriod.TWO_YEARS)
        );

        assertEquals(
                "5y",
                mapper.toRange(PortfolioHistoryPeriod.FIVE_YEARS)
        );

        assertEquals(
                "10y",
                mapper.toRange(PortfolioHistoryPeriod.TEN_YEARS)
        );

        assertEquals(
                "max",
                mapper.toRange(PortfolioHistoryPeriod.ALL)
        );
    }

    @Test
    void shouldThrowExceptionForCustomPeriod() {

        assertThrows(
                IllegalArgumentException.class,
                () -> mapper.toRange(
                        PortfolioHistoryPeriod.CUSTOM
                )
        );
    }
}