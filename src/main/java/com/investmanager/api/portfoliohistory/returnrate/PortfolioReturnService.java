package com.investmanager.api.portfoliohistory.returnrate;

import com.investmanager.api.asset.Asset;
import com.investmanager.api.income.Income;
import com.investmanager.api.income.repository.IncomeRepository;
import com.investmanager.api.portfolio.exception.PortfolioNotFoundException;
import com.investmanager.api.portfolio.repository.PortfolioRepository;
import com.investmanager.api.portfoliohistory.model.PortfolioHistoryDateRange;
import com.investmanager.api.portfoliohistory.model.PortfolioHistoryGranularity;
import com.investmanager.api.portfoliohistory.model.PortfolioHistoryPeriod;
import com.investmanager.api.portfoliohistory.service.HistoricalPositionCalculator;
import com.investmanager.api.portfoliohistory.service.HistoricalQuoteResolver;
import com.investmanager.api.portfoliohistory.service.PortfolioHistoryDateGenerator;
import com.investmanager.api.portfoliohistory.service.PortfolioHistoryDateRangeResolver;
import com.investmanager.api.quote.model.HistoricalQuote;
import com.investmanager.api.quote.service.HistoricalQuoteService;
import com.investmanager.api.transaction.Transaction;
import com.investmanager.api.transaction.repository.TransactionRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class PortfolioReturnService {

    private static final int QUOTE_LOOKBACK_DAYS = 7;

    private final PortfolioRepository portfolioRepository;
    private final TransactionRepository transactionRepository;
    private final IncomeRepository incomeRepository;
    private final HistoricalQuoteService historicalQuoteService;
    private final PortfolioHistoryDateGenerator dateGenerator;
    private final PortfolioHistoryDateRangeResolver dateRangeResolver;
    private final HistoricalPositionCalculator positionCalculator;
    private final HistoricalQuoteResolver quoteResolver;
    private final PortfolioHistoricalValueCalculator historicalValueCalculator;

    private final PortfolioDailyMovementsCalculator movementsCalculator;
    private final PortfolioDailyReturnCalculator dailyReturnCalculator;
    private final PortfolioCumulativeReturnCalculator cumulativeReturnCalculator;

    public PortfolioReturnService(
            PortfolioRepository portfolioRepository,
            TransactionRepository transactionRepository,
            IncomeRepository incomeRepository,
            HistoricalQuoteService historicalQuoteService,
            PortfolioHistoryDateGenerator dateGenerator,
            PortfolioHistoryDateRangeResolver dateRangeResolver,
            HistoricalPositionCalculator positionCalculator,
            HistoricalQuoteResolver quoteResolver,
            PortfolioHistoricalValueCalculator historicalValueCalculator) {

        this.portfolioRepository = portfolioRepository;
        this.transactionRepository = transactionRepository;
        this.incomeRepository = incomeRepository;
        this.historicalQuoteService = historicalQuoteService;
        this.dateGenerator = dateGenerator;
        this.dateRangeResolver = dateRangeResolver;
        this.positionCalculator = positionCalculator;
        this.quoteResolver = quoteResolver;
        this.historicalValueCalculator = historicalValueCalculator;

        this.movementsCalculator =
                new PortfolioDailyMovementsCalculator();

        this.dailyReturnCalculator =
                new PortfolioDailyReturnCalculator();

        this.cumulativeReturnCalculator =
                new PortfolioCumulativeReturnCalculator();
    }

    public PortfolioReturnResponse getReturns(
            Long portfolioId,
            Long userId,
            PortfolioHistoryPeriod period,
            LocalDate customStartDate,
            LocalDate customEndDate,
            LocalDate endDate) {

        validatePortfolioOwnership(
                portfolioId,
                userId
        );

        List<Transaction> transactions =
                transactionRepository
                        .findByPortfolioIdAndPortfolioUserIdOrderByTransactionDateAscIdAsc(
                                portfolioId,
                                userId
                        );

        List<Income> incomes =
                incomeRepository
                        .findByPortfolioIdAndPortfolioUserIdOrderByPaymentDateAscIdAsc(
                                portfolioId,
                                userId
                        );

        LocalDate firstTransactionDate =
                transactions.isEmpty()
                        ? null
                        : transactions.getFirst().getTransactionDate();

        PortfolioHistoryDateRange dateRange =
                dateRangeResolver.resolve(
                        period,
                        endDate,
                        customStartDate,
                        customEndDate,
                        firstTransactionDate
                );

        LocalDate effectiveStartDate =
                firstTransactionDate != null
                        && firstTransactionDate.isAfter(dateRange.startDate())
                        ? firstTransactionDate
                        : dateRange.startDate();

        PortfolioHistoryDateRange effectiveDateRange =
                new PortfolioHistoryDateRange(
                        effectiveStartDate,
                        dateRange.endDate()
                );

        List<LocalDate> returnDates =
                dateGenerator.generate(
                        effectiveDateRange.startDate(),
                        effectiveDateRange.endDate(),
                        PortfolioHistoryGranularity.DAILY
                );

        Map<Asset, List<Transaction>> transactionsByAsset =
                transactions.stream()
                        .collect(
                                Collectors.groupingBy(
                                        Transaction::getAsset
                                )
                        );

        Map<Asset, List<HistoricalQuote>> quotesByAsset =
                loadHistoricalQuotes(
                        transactionsByAsset,
                        effectiveDateRange
                );

        Map<LocalDate, PortfolioDailyMovements> movementsByDate =
                movementsCalculator
                        .calculate(
                                transactions,
                                incomes
                        )
                        .stream()
                        .collect(
                                Collectors.toMap(
                                        PortfolioDailyMovements::date,
                                        Function.identity()
                                )
                        );

        List<BigDecimal> dailyReturns =
                new ArrayList<>();

        List<PortfolioReturnSeriesPoint> points =
                new ArrayList<>();

        for (LocalDate date : returnDates) {

            LocalDate previousDate =
                    date.minusDays(1);

            validateHistoricalQuotes(
                    previousDate,
                    transactionsByAsset,
                    quotesByAsset
            );

            validateHistoricalQuotes(
                    date,
                    transactionsByAsset,
                    quotesByAsset
            );

            BigDecimal previousMarketValue =
                    historicalValueCalculator.calculate(
                            previousDate,
                            transactionsByAsset,
                            quotesByAsset
                    );

            BigDecimal currentMarketValue =
                    historicalValueCalculator.calculate(
                            date,
                            transactionsByAsset,
                            quotesByAsset
                    );

            PortfolioDailyMovements movements =
                    movementsByDate.getOrDefault(
                            date,
                            new PortfolioDailyMovements(
                                    date,
                                    BigDecimal.ZERO,
                                    BigDecimal.ZERO,
                                    BigDecimal.ZERO
                            )
                    );

            PortfolioDailyReturnInput input =
                    new PortfolioDailyReturnInput(
                            date,
                            previousMarketValue,
                            currentMarketValue,
                            movements.purchases(),
                            movements.sales(),
                            movements.incomes()
                    );

            BigDecimal dailyReturn =
                    date.equals(firstTransactionDate)
                            ? BigDecimal.ZERO
                            : dailyReturnCalculator.calculate(
                            input
                    );

            dailyReturns.add(
                    dailyReturn
            );

            BigDecimal cumulativeReturn =
                    cumulativeReturnCalculator.calculate(
                            dailyReturns
                    );

            points.add(
                    new PortfolioReturnSeriesPoint(
                            date,
                            dailyReturn,
                            cumulativeReturn
                    )
            );
        }

        BigDecimal totalReturn =
                cumulativeReturnCalculator.calculate(
                        dailyReturns
                );

        return new PortfolioReturnResponse(
                portfolioId,
                effectiveDateRange.startDate(),
                effectiveDateRange.endDate(),
                totalReturn,
                points
        );
    }

    private Map<Asset, List<HistoricalQuote>> loadHistoricalQuotes(
            Map<Asset, List<Transaction>> transactionsByAsset,
            PortfolioHistoryDateRange dateRange) {

        LocalDate quoteStartDate =
                dateRange.startDate()
                        .minusDays(
                                QUOTE_LOOKBACK_DAYS
                        );

        return transactionsByAsset
                .keySet()
                .stream()
                .collect(
                        Collectors.toMap(
                                asset -> asset,
                                asset ->
                                        historicalQuoteService
                                                .getHistoricalQuotes(
                                                        asset.getTicker(),
                                                        quoteStartDate,
                                                        dateRange.endDate(),
                                                        PortfolioHistoryGranularity.DAILY
                                                )
                        )
                );
    }

    private void validateHistoricalQuotes(
            LocalDate date,
            Map<Asset, List<Transaction>> transactionsByAsset,
            Map<Asset, List<HistoricalQuote>> quotesByAsset) {

        for (Map.Entry<Asset, List<Transaction>> entry
                : transactionsByAsset.entrySet()) {

            Asset asset =
                    entry.getKey();

            List<Transaction> assetTransactions =
                    entry.getValue();

            BigDecimal quantity =
                    positionCalculator.calculateQuantity(
                            assetTransactions,
                            date
                    );

            if (quantity.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }

            List<HistoricalQuote> quotes =
                    quotesByAsset.get(asset);

            BigDecimal closePrice =
                    quoteResolver.resolveClosePrice(
                            quotes,
                            date
                    );

            if (closePrice == null) {
                throw new HistoricalQuoteUnavailableException(
                        asset.getTicker(),
                        date
                );
            }
        }
    }

    private void validatePortfolioOwnership(
            Long portfolioId,
            Long userId) {

        portfolioRepository
                .findByIdAndUserId(
                        portfolioId,
                        userId
                )
                .orElseThrow(
                        () ->
                                new PortfolioNotFoundException(
                                        portfolioId
                                )
                );
    }
}