package com.investmanager.api.portfoliohistory.service;

import com.investmanager.api.portfoliohistory.model.PortfolioHistoryGranularity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PortfolioHistoryDateGeneratorTest {

    private PortfolioHistoryDateGenerator dateGenerator;

    @BeforeEach
    void setUp() {
        dateGenerator = new PortfolioHistoryDateGenerator();
    }

    @Test
    void shouldGenerateDailyDates() {

        List<LocalDate> dates =
                dateGenerator.generate(
                        LocalDate.of(2026, 9, 1),
                        LocalDate.of(2026, 9, 5),
                        PortfolioHistoryGranularity.DAILY
                );

        assertEquals(
                List.of(
                        LocalDate.of(2026, 9, 1),
                        LocalDate.of(2026, 9, 2),
                        LocalDate.of(2026, 9, 3),
                        LocalDate.of(2026, 9, 4),
                        LocalDate.of(2026, 9, 5)
                ),
                dates
        );
    }

    @Test
    void shouldGenerateWeeklyDatesAndIncludeEndDate() {

        List<LocalDate> dates =
                dateGenerator.generate(
                        LocalDate.of(2026, 9, 1),
                        LocalDate.of(2026, 9, 18),
                        PortfolioHistoryGranularity.WEEKLY
                );

        assertEquals(
                List.of(
                        LocalDate.of(2026, 9, 1),
                        LocalDate.of(2026, 9, 8),
                        LocalDate.of(2026, 9, 15),
                        LocalDate.of(2026, 9, 18)
                ),
                dates
        );
    }

    @Test
    void shouldGenerateMonthlyDatesAndIncludeEndDate() {

        List<LocalDate> dates =
                dateGenerator.generate(
                        LocalDate.of(2026, 1, 10),
                        LocalDate.of(2026, 4, 25),
                        PortfolioHistoryGranularity.MONTHLY
                );

        assertEquals(
                List.of(
                        LocalDate.of(2026, 1, 10),
                        LocalDate.of(2026, 2, 10),
                        LocalDate.of(2026, 3, 10),
                        LocalDate.of(2026, 4, 10),
                        LocalDate.of(2026, 4, 25)
                ),
                dates
        );
    }

    @Test
    void shouldNotDuplicateEndDateWhenAlreadyGenerated() {

        List<LocalDate> dates =
                dateGenerator.generate(
                        LocalDate.of(2026, 9, 1),
                        LocalDate.of(2026, 9, 15),
                        PortfolioHistoryGranularity.WEEKLY
                );

        assertEquals(
                List.of(
                        LocalDate.of(2026, 9, 1),
                        LocalDate.of(2026, 9, 8),
                        LocalDate.of(2026, 9, 15)
                ),
                dates
        );
    }

    @Test
    void shouldGenerateSingleDateWhenStartAndEndAreEqual() {

        LocalDate date =
                LocalDate.of(2026, 9, 18);

        List<LocalDate> dates =
                dateGenerator.generate(
                        date,
                        date,
                        PortfolioHistoryGranularity.DAILY
                );

        assertEquals(
                List.of(date),
                dates
        );
    }

    @Test
    void shouldThrowExceptionWhenDatesAreMissing() {

        assertThrows(
                IllegalArgumentException.class,
                () -> dateGenerator.generate(
                        null,
                        null,
                        PortfolioHistoryGranularity.DAILY
                )
        );
    }

    @Test
    void shouldThrowExceptionWhenGranularityIsMissing() {

        assertThrows(
                IllegalArgumentException.class,
                () -> dateGenerator.generate(
                        LocalDate.of(2026, 9, 1),
                        LocalDate.of(2026, 9, 18),
                        null
                )
        );
    }

    @Test
    void shouldThrowExceptionWhenEndDateIsBeforeStartDate() {

        assertThrows(
                IllegalArgumentException.class,
                () -> dateGenerator.generate(
                        LocalDate.of(2026, 9, 18),
                        LocalDate.of(2026, 9, 17),
                        PortfolioHistoryGranularity.DAILY
                )
        );
    }
}