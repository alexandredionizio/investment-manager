package com.investmanager.api.quote.client.mapper;

import com.investmanager.api.portfoliohistory.model.PortfolioHistoryGranularity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BrapiHistoricalIntervalMapperTest {

    private BrapiHistoricalIntervalMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new BrapiHistoricalIntervalMapper();
    }

    @Test
    void shouldMapDailyGranularityToBrapiInterval() {

        assertEquals(
                "1d",
                mapper.toInterval(
                        PortfolioHistoryGranularity.DAILY
                )
        );
    }

    @Test
    void shouldMapWeeklyGranularityToBrapiInterval() {

        assertEquals(
                "1wk",
                mapper.toInterval(
                        PortfolioHistoryGranularity.WEEKLY
                )
        );
    }

    @Test
    void shouldMapMonthlyGranularityToBrapiInterval() {

        assertEquals(
                "1mo",
                mapper.toInterval(
                        PortfolioHistoryGranularity.MONTHLY
                )
        );
    }
}