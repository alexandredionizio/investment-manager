package com.investmanager.api.benchmark.service;

import com.investmanager.api.benchmark.calculator.CdiReturnCalculator;
import com.investmanager.api.benchmark.calculator.IbovReturnCalculator;
import com.investmanager.api.benchmark.dto.BenchmarkComparisonPointResponse;
import com.investmanager.api.benchmark.entity.BenchmarkQuote;
import com.investmanager.api.portfoliohistory.model.PortfolioHistoryPeriod;
import com.investmanager.api.portfoliohistory.returnrate.PortfolioReturnResponse;
import com.investmanager.api.portfoliohistory.returnrate.PortfolioReturnSeriesPoint;
import com.investmanager.api.portfoliohistory.returnrate.PortfolioReturnService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class BenchmarkComparisonService {

    private static final int BENCHMARK_LOOKBACK_DAYS = 7;

    private final PortfolioReturnService portfolioReturnService;
    private final CdiBenchmarkService cdiBenchmarkService;
    private final IbovBenchmarkService ibovBenchmarkService;
    private final CdiReturnCalculator cdiReturnCalculator;
    private final IbovReturnCalculator ibovReturnCalculator;

    public BenchmarkComparisonService(
            PortfolioReturnService portfolioReturnService,
            CdiBenchmarkService cdiBenchmarkService,
            IbovBenchmarkService ibovBenchmarkService,
            CdiReturnCalculator cdiReturnCalculator,
            IbovReturnCalculator ibovReturnCalculator) {

        this.portfolioReturnService = portfolioReturnService;
        this.cdiBenchmarkService = cdiBenchmarkService;
        this.ibovBenchmarkService = ibovBenchmarkService;
        this.cdiReturnCalculator = cdiReturnCalculator;
        this.ibovReturnCalculator = ibovReturnCalculator;
    }

    public List<BenchmarkComparisonPointResponse> getComparison(
            Long portfolioId,
            Long userId,
            PortfolioHistoryPeriod period,
            LocalDate customStartDate,
            LocalDate customEndDate,
            LocalDate endDate) {

        PortfolioReturnResponse portfolioReturns =
                portfolioReturnService.getReturns(
                        portfolioId,
                        userId,
                        period,
                        customStartDate,
                        customEndDate,
                        endDate
                );

        LocalDate effectiveStartDate =
                portfolioReturns.startDate();

        LocalDate effectiveEndDate =
                portfolioReturns.endDate();

        List<BenchmarkQuote> cdiQuotes =
                cdiBenchmarkService.load(
                        effectiveStartDate,
                        effectiveEndDate
                );

        List<BenchmarkQuote> ibovQuotes =
                ibovBenchmarkService.load(
                        effectiveStartDate,
                        effectiveEndDate
                );

        boolean cdiHasStartDate =
                hasQuoteOnDate(
                        cdiQuotes,
                        effectiveStartDate
                );

        boolean ibovHasStartDate =
                hasQuoteOnDate(
                        ibovQuotes,
                        effectiveStartDate
                );

        if (!cdiHasStartDate) {

            cdiQuotes =
                    cdiBenchmarkService.load(
                            effectiveStartDate.minusDays(
                                    BENCHMARK_LOOKBACK_DAYS
                            ),
                            effectiveEndDate
                    );
        }

        if (!ibovHasStartDate) {

            ibovQuotes =
                    ibovBenchmarkService.load(
                            effectiveStartDate.minusDays(
                                    BENCHMARK_LOOKBACK_DAYS
                            ),
                            effectiveEndDate
                    );
        }

        List<CdiReturnCalculator.ReturnPoint> cdiReturns =
                cdiReturnCalculator.calculate(
                        prepareCdiQuotes(
                                cdiQuotes,
                                effectiveStartDate
                        )
                );

        List<IbovReturnCalculator.ReturnPoint> ibovReturns =
                ibovReturnCalculator.calculate(
                        prepareIbovQuotes(
                                ibovQuotes,
                                effectiveStartDate
                        )
                );

        Map<LocalDate, BigDecimal> cdiByDate =
                new HashMap<>();

        for (CdiReturnCalculator.ReturnPoint point
                : cdiReturns) {

            if (!point.date().isBefore(
                    effectiveStartDate
            )) {

                cdiByDate.put(
                        point.date(),
                        point.accumulatedReturn()
                );
            }
        }

        Map<LocalDate, BigDecimal> ibovByDate =
                new HashMap<>();

        for (IbovReturnCalculator.ReturnPoint point
                : ibovReturns) {

            if (!point.date().isBefore(
                    effectiveStartDate
            )) {

                ibovByDate.put(
                        point.date(),
                        point.accumulatedReturn()
                );
            }
        }

        List<BenchmarkComparisonPointResponse> result =
                new ArrayList<>();

        BigDecimal lastCdiReturn =
                BigDecimal.ZERO;

        BigDecimal lastIbovReturn =
                BigDecimal.ZERO;

        for (PortfolioReturnSeriesPoint portfolioPoint
                : portfolioReturns.points()) {

            LocalDate date =
                    portfolioPoint.date();

            if (cdiByDate.containsKey(date)) {

                lastCdiReturn =
                        cdiByDate.get(date);
            }

            if (ibovByDate.containsKey(date)) {

                lastIbovReturn =
                        ibovByDate.get(date);
            }

            result.add(
                    new BenchmarkComparisonPointResponse(
                            date,
                            portfolioPoint.cumulativeReturn(),
                            lastCdiReturn,
                            lastIbovReturn
                    )
            );
        }

        return result;
    }

    private boolean hasQuoteOnDate(
            List<BenchmarkQuote> quotes,
            LocalDate date) {

        return quotes.stream()
                .anyMatch(quote ->
                        quote.getDate().equals(date)
                );
    }

    private List<BenchmarkQuote> prepareCdiQuotes(
            List<BenchmarkQuote> quotes,
            LocalDate effectiveStartDate) {

        List<BenchmarkQuote> orderedQuotes =
                quotes.stream()
                        .sorted((first, second) ->
                                first.getDate()
                                        .compareTo(
                                                second.getDate()
                                        )
                        )
                        .toList();

        boolean hasStartDate =
                hasQuoteOnDate(
                        orderedQuotes,
                        effectiveStartDate
                );

        if (hasStartDate) {

            return orderedQuotes.stream()
                    .filter(quote ->
                            !quote.getDate().isBefore(
                                    effectiveStartDate
                            )
                    )
                    .toList();
        }

        BenchmarkQuote previousQuote =
                findLastQuoteBefore(
                        orderedQuotes,
                        effectiveStartDate
                );

        List<BenchmarkQuote> result =
                new ArrayList<>();

        if (previousQuote != null) {
            result.add(previousQuote);
        }

        orderedQuotes.stream()
                .filter(quote ->
                        quote.getDate().isAfter(
                                effectiveStartDate
                        )
                )
                .forEach(result::add);

        return result;
    }

    private List<BenchmarkQuote> prepareIbovQuotes(
            List<BenchmarkQuote> quotes,
            LocalDate effectiveStartDate) {

        List<BenchmarkQuote> orderedQuotes =
                quotes.stream()
                        .sorted((first, second) ->
                                first.getDate()
                                        .compareTo(
                                                second.getDate()
                                        )
                        )
                        .toList();

        boolean hasStartDate =
                hasQuoteOnDate(
                        orderedQuotes,
                        effectiveStartDate
                );

        if (hasStartDate) {

            return orderedQuotes.stream()
                    .filter(quote ->
                            !quote.getDate().isBefore(
                                    effectiveStartDate
                            )
                    )
                    .toList();
        }

        BenchmarkQuote previousQuote =
                findLastQuoteBefore(
                        orderedQuotes,
                        effectiveStartDate
                );

        List<BenchmarkQuote> result =
                new ArrayList<>();

        if (previousQuote != null) {
            result.add(previousQuote);
        }

        orderedQuotes.stream()
                .filter(quote ->
                        quote.getDate().isAfter(
                                effectiveStartDate
                        )
                )
                .forEach(result::add);

        return result;
    }

    private BenchmarkQuote findLastQuoteBefore(
            List<BenchmarkQuote> quotes,
            LocalDate date) {

        BenchmarkQuote result = null;

        for (BenchmarkQuote quote : quotes) {

            if (quote.getDate().isBefore(date)) {
                result = quote;
            } else {
                break;
            }
        }

        return result;
    }
}