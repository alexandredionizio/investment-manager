package com.investmanager.api.benchmark.service;

import com.investmanager.api.benchmark.calculator.CdiReturnCalculator;
import com.investmanager.api.benchmark.calculator.IbovReturnCalculator;
import com.investmanager.api.benchmark.dto.BenchmarkComparisonPointResponse;
import com.investmanager.api.benchmark.entity.BenchmarkQuote;
import com.investmanager.api.benchmark.model.BenchmarkType;
import com.investmanager.api.portfoliohistory.model.PortfolioHistoryPeriod;
import com.investmanager.api.portfoliohistory.returnrate.PortfolioReturnResponse;
import com.investmanager.api.portfoliohistory.returnrate.PortfolioReturnSeriesPoint;
import com.investmanager.api.portfoliohistory.returnrate.PortfolioReturnService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BenchmarkComparisonServiceTest {

    @Mock
    private PortfolioReturnService portfolioReturnService;

    @Mock
    private CdiBenchmarkService cdiBenchmarkService;

    @Mock
    private IbovBenchmarkService ibovBenchmarkService;

    private BenchmarkComparisonService service;

    @BeforeEach
    void setUp() {

        service = new BenchmarkComparisonService(
                portfolioReturnService,
                cdiBenchmarkService,
                ibovBenchmarkService,
                new CdiReturnCalculator(),
                new IbovReturnCalculator()
        );
    }

    @Test
    void shouldCombinePortfolioCdiAndIbovReturns() {

        Long portfolioId = 1L;
        Long userId = 10L;

        LocalDate startDate =
                LocalDate.of(2026, 9, 1);

        LocalDate endDate =
                LocalDate.of(2026, 9, 3);

        PortfolioHistoryPeriod period =
                PortfolioHistoryPeriod.ONE_MONTH;

        PortfolioReturnResponse portfolioResponse =
                new PortfolioReturnResponse(
                        portfolioId,
                        startDate,
                        endDate,
                        new BigDecimal("0.0200000000"),
                        List.of(
                                portfolioPoint(
                                        LocalDate.of(2026, 9, 1),
                                        "0.0000000000"
                                ),
                                portfolioPoint(
                                        LocalDate.of(2026, 9, 2),
                                        "0.0100000000"
                                ),
                                portfolioPoint(
                                        LocalDate.of(2026, 9, 3),
                                        "0.0200000000"
                                )
                        )
                );

        when(portfolioReturnService.getReturns(
                portfolioId,
                userId,
                period,
                null,
                null,
                endDate
        )).thenReturn(portfolioResponse);

        when(cdiBenchmarkService.load(
                startDate,
                endDate
        )).thenReturn(
                List.of(
                        benchmarkQuote(
                                BenchmarkType.CDI,
                                LocalDate.of(2026, 9, 1),
                                "0.05000000"
                        ),
                        benchmarkQuote(
                                BenchmarkType.CDI,
                                LocalDate.of(2026, 9, 2),
                                "0.05000000"
                        ),
                        benchmarkQuote(
                                BenchmarkType.CDI,
                                LocalDate.of(2026, 9, 3),
                                "0.05000000"
                        )
                )
        );

        when(ibovBenchmarkService.load(
                startDate,
                endDate
        )).thenReturn(
                List.of(
                        benchmarkQuote(
                                BenchmarkType.IBOV,
                                LocalDate.of(2026, 9, 1),
                                "140000.00"
                        ),
                        benchmarkQuote(
                                BenchmarkType.IBOV,
                                LocalDate.of(2026, 9, 2),
                                "141400.00"
                        ),
                        benchmarkQuote(
                                BenchmarkType.IBOV,
                                LocalDate.of(2026, 9, 3),
                                "142800.00"
                        )
                )
        );

        List<BenchmarkComparisonPointResponse> result =
                service.getComparison(
                        portfolioId,
                        userId,
                        period,
                        null,
                        null,
                        endDate
                );

        assertThat(result).hasSize(3);

        assertThat(result.get(0).portfolioReturn())
                .isEqualByComparingTo("0");

        assertThat(result.get(0).cdiReturn())
                .isEqualByComparingTo("0");

        assertThat(result.get(0).ibovReturn())
                .isEqualByComparingTo("0");

        assertThat(result.get(1).portfolioReturn())
                .isEqualByComparingTo("0.0100000000");

        assertThat(result.get(1).cdiReturn())
                .isEqualByComparingTo("0.0005000000");

        assertThat(result.get(1).ibovReturn())
                .isEqualByComparingTo("0.0100000000");

        assertThat(result.get(2).portfolioReturn())
                .isEqualByComparingTo("0.0200000000");

        assertThat(result.get(2).cdiReturn())
                .isEqualByComparingTo("0.0010002500");

        assertThat(result.get(2).ibovReturn())
                .isEqualByComparingTo("0.0200000000");

        verify(cdiBenchmarkService)
                .load(startDate, endDate);

        verify(ibovBenchmarkService)
                .load(startDate, endDate);
    }

    @Test
    void shouldCarryForwardLastBenchmarkReturnsWhenDateHasNoQuote() {

        Long portfolioId = 1L;
        Long userId = 10L;

        LocalDate friday =
                LocalDate.of(2026, 9, 18);

        LocalDate saturday =
                LocalDate.of(2026, 9, 19);

        LocalDate sunday =
                LocalDate.of(2026, 9, 20);

        PortfolioHistoryPeriod period =
                PortfolioHistoryPeriod.ONE_MONTH;

        PortfolioReturnResponse portfolioResponse =
                new PortfolioReturnResponse(
                        portfolioId,
                        friday,
                        sunday,
                        BigDecimal.ZERO,
                        List.of(
                                portfolioPoint(friday, "0"),
                                portfolioPoint(saturday, "0"),
                                portfolioPoint(sunday, "0")
                        )
                );

        when(portfolioReturnService.getReturns(
                portfolioId,
                userId,
                period,
                null,
                null,
                sunday
        )).thenReturn(portfolioResponse);

        when(cdiBenchmarkService.load(
                friday,
                sunday
        )).thenReturn(
                List.of(
                        benchmarkQuote(
                                BenchmarkType.CDI,
                                friday,
                                "0.05000000"
                        )
                )
        );

        when(ibovBenchmarkService.load(
                friday,
                sunday
        )).thenReturn(
                List.of(
                        benchmarkQuote(
                                BenchmarkType.IBOV,
                                friday,
                                "140000.00"
                        )
                )
        );

        List<BenchmarkComparisonPointResponse> result =
                service.getComparison(
                        portfolioId,
                        userId,
                        period,
                        null,
                        null,
                        sunday
                );

        assertThat(result).hasSize(3);

        assertThat(result.get(0).cdiReturn())
                .isEqualByComparingTo("0");

        assertThat(result.get(1).cdiReturn())
                .isEqualByComparingTo("0");

        assertThat(result.get(2).cdiReturn())
                .isEqualByComparingTo("0");

        assertThat(result.get(0).ibovReturn())
                .isEqualByComparingTo("0");

        assertThat(result.get(1).ibovReturn())
                .isEqualByComparingTo("0");

        assertThat(result.get(2).ibovReturn())
                .isEqualByComparingTo("0");
    }

    @Test
    void shouldUseEffectivePortfolioPeriodForBenchmarks() {

        Long portfolioId = 1L;
        Long userId = 10L;

        LocalDate requestedEndDate =
                LocalDate.of(2026, 9, 22);

        LocalDate effectiveStartDate =
                LocalDate.of(2026, 7, 10);

        PortfolioHistoryPeriod period =
                PortfolioHistoryPeriod.ONE_YEAR;

        PortfolioReturnResponse portfolioResponse =
                new PortfolioReturnResponse(
                        portfolioId,
                        effectiveStartDate,
                        requestedEndDate,
                        BigDecimal.ZERO,
                        List.of(
                                portfolioPoint(
                                        effectiveStartDate,
                                        "0"
                                )
                        )
                );

        when(portfolioReturnService.getReturns(
                portfolioId,
                userId,
                period,
                null,
                null,
                requestedEndDate
        )).thenReturn(portfolioResponse);

        when(cdiBenchmarkService.load(
                effectiveStartDate,
                requestedEndDate
        )).thenReturn(List.of());

        when(ibovBenchmarkService.load(
                effectiveStartDate,
                requestedEndDate
        )).thenReturn(List.of());

        LocalDate lookbackStartDate =
                effectiveStartDate.minusDays(7);

        when(cdiBenchmarkService.load(
                lookbackStartDate,
                requestedEndDate
        )).thenReturn(List.of());

        when(ibovBenchmarkService.load(
                lookbackStartDate,
                requestedEndDate
        )).thenReturn(List.of());

        service.getComparison(
                portfolioId,
                userId,
                period,
                null,
                null,
                requestedEndDate
        );

        verify(cdiBenchmarkService)
                .load(
                        effectiveStartDate,
                        requestedEndDate
                );

        verify(ibovBenchmarkService)
                .load(
                        effectiveStartDate,
                        requestedEndDate
                );

        verify(cdiBenchmarkService)
                .load(
                        lookbackStartDate,
                        requestedEndDate
                );

        verify(ibovBenchmarkService)
                .load(
                        lookbackStartDate,
                        requestedEndDate
                );
    }

    @Test
    void shouldCalculateBenchmarksCorrectlyWhenPeriodStartsOnWeekend() {

        Long portfolioId = 1L;
        Long userId = 10L;

        LocalDate friday =
                LocalDate.of(2026, 9, 18);

        LocalDate saturday =
                LocalDate.of(2026, 9, 19);

        LocalDate sunday =
                LocalDate.of(2026, 9, 20);

        LocalDate monday =
                LocalDate.of(2026, 9, 21);

        LocalDate lookbackStartDate =
                saturday.minusDays(7);

        PortfolioHistoryPeriod period =
                PortfolioHistoryPeriod.CUSTOM;

        PortfolioReturnResponse portfolioResponse =
                new PortfolioReturnResponse(
                        portfolioId,
                        saturday,
                        monday,
                        BigDecimal.ZERO,
                        List.of(
                                portfolioPoint(saturday, "0"),
                                portfolioPoint(sunday, "0"),
                                portfolioPoint(monday, "0")
                        )
                );

        when(portfolioReturnService.getReturns(
                portfolioId,
                userId,
                period,
                saturday,
                monday,
                null
        )).thenReturn(portfolioResponse);

        when(cdiBenchmarkService.load(
                saturday,
                monday
        )).thenReturn(
                List.of(
                        benchmarkQuote(
                                BenchmarkType.CDI,
                                monday,
                                "0.05000000"
                        )
                )
        );

        when(ibovBenchmarkService.load(
                saturday,
                monday
        )).thenReturn(
                List.of(
                        benchmarkQuote(
                                BenchmarkType.IBOV,
                                monday,
                                "141400.00"
                        )
                )
        );

        when(cdiBenchmarkService.load(
                lookbackStartDate,
                monday
        )).thenReturn(
                List.of(
                        benchmarkQuote(
                                BenchmarkType.CDI,
                                friday,
                                "0.05000000"
                        ),
                        benchmarkQuote(
                                BenchmarkType.CDI,
                                monday,
                                "0.05000000"
                        )
                )
        );

        when(ibovBenchmarkService.load(
                lookbackStartDate,
                monday
        )).thenReturn(
                List.of(
                        benchmarkQuote(
                                BenchmarkType.IBOV,
                                friday,
                                "140000.00"
                        ),
                        benchmarkQuote(
                                BenchmarkType.IBOV,
                                monday,
                                "141400.00"
                        )
                )
        );

        List<BenchmarkComparisonPointResponse> result =
                service.getComparison(
                        portfolioId,
                        userId,
                        period,
                        saturday,
                        monday,
                        null
                );

        assertThat(result).hasSize(3);

        assertThat(result.get(0).date())
                .isEqualTo(saturday);

        assertThat(result.get(0).cdiReturn())
                .isEqualByComparingTo("0");

        assertThat(result.get(0).ibovReturn())
                .isEqualByComparingTo("0");

        assertThat(result.get(1).date())
                .isEqualTo(sunday);

        assertThat(result.get(1).cdiReturn())
                .isEqualByComparingTo("0");

        assertThat(result.get(1).ibovReturn())
                .isEqualByComparingTo("0");

        assertThat(result.get(2).date())
                .isEqualTo(monday);

        assertThat(result.get(2).cdiReturn())
                .isEqualByComparingTo("0.0005000000");

        assertThat(result.get(2).ibovReturn())
                .isEqualByComparingTo("0.0100000000");

        verify(cdiBenchmarkService)
                .load(saturday, monday);

        verify(ibovBenchmarkService)
                .load(saturday, monday);

        verify(cdiBenchmarkService)
                .load(lookbackStartDate, monday);

        verify(ibovBenchmarkService)
                .load(lookbackStartDate, monday);
    }

    private PortfolioReturnSeriesPoint portfolioPoint(
            LocalDate date,
            String cumulativeReturn) {

        return new PortfolioReturnSeriesPoint(
                date,
                BigDecimal.ZERO,
                new BigDecimal(cumulativeReturn)
        );
    }

    private BenchmarkQuote benchmarkQuote(
            BenchmarkType benchmark,
            LocalDate date,
            String value) {

        return new BenchmarkQuote(
                benchmark,
                date,
                new BigDecimal(value)
        );
    }
}