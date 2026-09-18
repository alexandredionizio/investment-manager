package com.investmanager.api.portfoliohistory.service;

import com.investmanager.api.portfoliohistory.model.PortfolioHistoryDateRange;
import com.investmanager.api.portfoliohistory.model.PortfolioHistoryPeriod;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PortfolioHistoryDateRangeResolverTest {

    private PortfolioHistoryDateRangeResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new PortfolioHistoryDateRangeResolver();
    }

    @Test
    void shouldResolveOneMonthPeriod() {

        LocalDate endDate =
                LocalDate.of(2026, 9, 18);

        PortfolioHistoryDateRange range =
                resolver.resolve(
                        PortfolioHistoryPeriod.ONE_MONTH,
                        endDate,
                        null,
                        null,
                        null
                );

        assertEquals(
                LocalDate.of(2026, 8, 18),
                range.startDate()
        );

        assertEquals(
                endDate,
                range.endDate()
        );
    }

    @Test
    void shouldResolveThreeMonthsPeriod() {

        LocalDate endDate =
                LocalDate.of(2026, 9, 18);

        PortfolioHistoryDateRange range =
                resolver.resolve(
                        PortfolioHistoryPeriod.THREE_MONTHS,
                        endDate,
                        null,
                        null,
                        null
                );

        assertEquals(
                LocalDate.of(2026, 6, 18),
                range.startDate()
        );

        assertEquals(
                endDate,
                range.endDate()
        );
    }

    @Test
    void shouldResolveSixMonthsPeriod() {

        LocalDate endDate =
                LocalDate.of(2026, 9, 18);

        PortfolioHistoryDateRange range =
                resolver.resolve(
                        PortfolioHistoryPeriod.SIX_MONTHS,
                        endDate,
                        null,
                        null,
                        null
                );

        assertEquals(
                LocalDate.of(2026, 3, 18),
                range.startDate()
        );

        assertEquals(
                endDate,
                range.endDate()
        );
    }

    @Test
    void shouldResolveOneYearPeriod() {

        LocalDate endDate =
                LocalDate.of(2026, 9, 18);

        PortfolioHistoryDateRange range =
                resolver.resolve(
                        PortfolioHistoryPeriod.ONE_YEAR,
                        endDate,
                        null,
                        null,
                        null
                );

        assertEquals(
                LocalDate.of(2025, 9, 18),
                range.startDate()
        );

        assertEquals(
                endDate,
                range.endDate()
        );
    }

    @Test
    void shouldResolveTwoYearsPeriod() {

        LocalDate endDate =
                LocalDate.of(2026, 9, 18);

        PortfolioHistoryDateRange range =
                resolver.resolve(
                        PortfolioHistoryPeriod.TWO_YEARS,
                        endDate,
                        null,
                        null,
                        null
                );

        assertEquals(
                LocalDate.of(2024, 9, 18),
                range.startDate()
        );

        assertEquals(
                endDate,
                range.endDate()
        );
    }

    @Test
    void shouldResolveFiveYearsPeriod() {

        LocalDate endDate =
                LocalDate.of(2026, 9, 18);

        PortfolioHistoryDateRange range =
                resolver.resolve(
                        PortfolioHistoryPeriod.FIVE_YEARS,
                        endDate,
                        null,
                        null,
                        null
                );

        assertEquals(
                LocalDate.of(2021, 9, 18),
                range.startDate()
        );

        assertEquals(
                endDate,
                range.endDate()
        );
    }

    @Test
    void shouldResolveTenYearsPeriod() {

        LocalDate endDate =
                LocalDate.of(2026, 9, 18);

        PortfolioHistoryDateRange range =
                resolver.resolve(
                        PortfolioHistoryPeriod.TEN_YEARS,
                        endDate,
                        null,
                        null,
                        null
                );

        assertEquals(
                LocalDate.of(2016, 9, 18),
                range.startDate()
        );

        assertEquals(
                endDate,
                range.endDate()
        );
    }

    @Test
    void shouldResolveAllPeriodUsingFirstTransactionDate() {

        LocalDate endDate =
                LocalDate.of(2026, 9, 18);

        LocalDate firstTransactionDate =
                LocalDate.of(2023, 4, 10);

        PortfolioHistoryDateRange range =
                resolver.resolve(
                        PortfolioHistoryPeriod.ALL,
                        endDate,
                        null,
                        null,
                        firstTransactionDate
                );

        assertEquals(
                firstTransactionDate,
                range.startDate()
        );

        assertEquals(
                endDate,
                range.endDate()
        );
    }

    @Test
    void shouldResolveCustomPeriod() {

        LocalDate customStartDate =
                LocalDate.of(2026, 1, 10);

        LocalDate customEndDate =
                LocalDate.of(2026, 7, 25);

        PortfolioHistoryDateRange range =
                resolver.resolve(
                        PortfolioHistoryPeriod.CUSTOM,
                        null,
                        customStartDate,
                        customEndDate,
                        null
                );

        assertEquals(
                customStartDate,
                range.startDate()
        );

        assertEquals(
                customEndDate,
                range.endDate()
        );
    }

    @Test
    void shouldThrowExceptionWhenCustomDatesAreMissing() {

        assertThrows(
                IllegalArgumentException.class,
                () -> resolver.resolve(
                        PortfolioHistoryPeriod.CUSTOM,
                        null,
                        null,
                        null,
                        null
                )
        );
    }

    @Test
    void shouldThrowExceptionWhenCustomEndDateIsBeforeStartDate() {

        assertThrows(
                IllegalArgumentException.class,
                () -> resolver.resolve(
                        PortfolioHistoryPeriod.CUSTOM,
                        null,
                        LocalDate.of(2026, 9, 18),
                        LocalDate.of(2026, 9, 17),
                        null
                )
        );
    }

    @Test
    void shouldThrowExceptionWhenAllHasNoTransactions() {

        assertThrows(
                IllegalArgumentException.class,
                () -> resolver.resolve(
                        PortfolioHistoryPeriod.ALL,
                        LocalDate.of(2026, 9, 18),
                        null,
                        null,
                        null
                )
        );
    }

    @Test
    void shouldThrowExceptionWhenPeriodIsNull() {

        assertThrows(
                IllegalArgumentException.class,
                () -> resolver.resolve(
                        null,
                        LocalDate.of(2026, 9, 18),
                        null,
                        null,
                        null
                )
        );
    }

    @Test
    void shouldThrowExceptionWhenEndDateIsNullForPredefinedPeriod() {

        assertThrows(
                IllegalArgumentException.class,
                () -> resolver.resolve(
                        PortfolioHistoryPeriod.ONE_YEAR,
                        null,
                        null,
                        null,
                        null
                )
        );
    }
}