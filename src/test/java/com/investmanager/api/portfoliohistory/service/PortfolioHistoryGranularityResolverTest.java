package com.investmanager.api.portfoliohistory.service;

import com.investmanager.api.portfoliohistory.model.PortfolioHistoryGranularity;
import com.investmanager.api.portfoliohistory.model.PortfolioHistoryPeriod;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PortfolioHistoryGranularityResolverTest {

    private PortfolioHistoryGranularityResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new PortfolioHistoryGranularityResolver();
    }

    @Test
    void shouldResolveDailyForShortPeriods() {

        assertEquals(
                PortfolioHistoryGranularity.DAILY,
                resolver.resolve(
                        PortfolioHistoryPeriod.ONE_MONTH,
                        null,
                        null
                )
        );

        assertEquals(
                PortfolioHistoryGranularity.DAILY,
                resolver.resolve(
                        PortfolioHistoryPeriod.THREE_MONTHS,
                        null,
                        null
                )
        );

        assertEquals(
                PortfolioHistoryGranularity.DAILY,
                resolver.resolve(
                        PortfolioHistoryPeriod.SIX_MONTHS,
                        null,
                        null
                )
        );
    }

    @Test
    void shouldResolveWeeklyForMediumPeriods() {

        assertEquals(
                PortfolioHistoryGranularity.WEEKLY,
                resolver.resolve(
                        PortfolioHistoryPeriod.ONE_YEAR,
                        null,
                        null
                )
        );

        assertEquals(
                PortfolioHistoryGranularity.WEEKLY,
                resolver.resolve(
                        PortfolioHistoryPeriod.TWO_YEARS,
                        null,
                        null
                )
        );
    }

    @Test
    void shouldResolveMonthlyForLongPeriods() {

        assertEquals(
                PortfolioHistoryGranularity.MONTHLY,
                resolver.resolve(
                        PortfolioHistoryPeriod.FIVE_YEARS,
                        null,
                        null
                )
        );

        assertEquals(
                PortfolioHistoryGranularity.MONTHLY,
                resolver.resolve(
                        PortfolioHistoryPeriod.TEN_YEARS,
                        null,
                        null
                )
        );

        assertEquals(
                PortfolioHistoryGranularity.MONTHLY,
                resolver.resolve(
                        PortfolioHistoryPeriod.ALL,
                        null,
                        null
                )
        );
    }

    @Test
    void shouldResolveDailyForCustomPeriodUpToSixMonths() {

        LocalDate startDate =
                LocalDate.of(2026, 1, 1);

        LocalDate endDate =
                LocalDate.of(2026, 7, 1);

        assertEquals(
                PortfolioHistoryGranularity.DAILY,
                resolver.resolve(
                        PortfolioHistoryPeriod.CUSTOM,
                        startDate,
                        endDate
                )
        );
    }

    @Test
    void shouldResolveWeeklyForCustomPeriodLongerThanSixMonthsUpToTwoYears() {

        LocalDate startDate =
                LocalDate.of(2026, 1, 1);

        LocalDate endDate =
                LocalDate.of(2028, 1, 1);

        assertEquals(
                PortfolioHistoryGranularity.WEEKLY,
                resolver.resolve(
                        PortfolioHistoryPeriod.CUSTOM,
                        startDate,
                        endDate
                )
        );
    }

    @Test
    void shouldResolveMonthlyForCustomPeriodLongerThanTwoYears() {

        LocalDate startDate =
                LocalDate.of(2026, 1, 1);

        LocalDate endDate =
                LocalDate.of(2028, 1, 2);

        assertEquals(
                PortfolioHistoryGranularity.MONTHLY,
                resolver.resolve(
                        PortfolioHistoryPeriod.CUSTOM,
                        startDate,
                        endDate
                )
        );
    }

    @Test
    void shouldThrowExceptionWhenCustomDatesAreMissing() {

        assertThrows(
                IllegalArgumentException.class,
                () -> resolver.resolve(
                        PortfolioHistoryPeriod.CUSTOM,
                        null,
                        null
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
                () -> resolver.resolve(
                        PortfolioHistoryPeriod.CUSTOM,
                        startDate,
                        endDate
                )
        );
    }
}