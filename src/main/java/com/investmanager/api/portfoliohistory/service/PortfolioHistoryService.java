package com.investmanager.api.portfoliohistory.service;

import com.investmanager.api.asset.Asset;
import com.investmanager.api.portfolio.exception.PortfolioNotFoundException;
import com.investmanager.api.portfolio.repository.PortfolioRepository;
import com.investmanager.api.portfoliohistory.dto.PortfolioHistoryResponse;
import com.investmanager.api.portfoliohistory.model.PortfolioHistoryDateRange;
import com.investmanager.api.portfoliohistory.model.PortfolioHistoryGranularity;
import com.investmanager.api.portfoliohistory.model.PortfolioHistoryPeriod;
import com.investmanager.api.portfoliohistory.model.PortfolioHistoryPoint;
import com.investmanager.api.quote.model.HistoricalQuote;
import com.investmanager.api.quote.service.HistoricalQuoteService;
import com.investmanager.api.transaction.Transaction;
import com.investmanager.api.transaction.repository.TransactionRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class PortfolioHistoryService {

    private static final int QUOTE_LOOKBACK_DAYS = 7;

    private final PortfolioRepository portfolioRepository;
    private final TransactionRepository transactionRepository;
    private final HistoricalQuoteService historicalQuoteService;
    private final PortfolioHistoryGranularityResolver granularityResolver;
    private final PortfolioHistoryDateRangeResolver dateRangeResolver;
    private final PortfolioHistoryDateGenerator dateGenerator;
    private final HistoricalPositionCalculator positionCalculator;
    private final HistoricalQuoteResolver quoteResolver;

    public PortfolioHistoryService(
            PortfolioRepository portfolioRepository,
            TransactionRepository transactionRepository,
            HistoricalQuoteService historicalQuoteService,
            PortfolioHistoryGranularityResolver granularityResolver,
            PortfolioHistoryDateRangeResolver dateRangeResolver,
            PortfolioHistoryDateGenerator dateGenerator,
            HistoricalPositionCalculator positionCalculator,
            HistoricalQuoteResolver quoteResolver) {

        this.portfolioRepository = portfolioRepository;
        this.transactionRepository = transactionRepository;
        this.historicalQuoteService = historicalQuoteService;
        this.granularityResolver = granularityResolver;
        this.dateRangeResolver = dateRangeResolver;
        this.dateGenerator = dateGenerator;
        this.positionCalculator = positionCalculator;
        this.quoteResolver = quoteResolver;
    }

    public PortfolioHistoryResponse getHistory(
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
                        .findByPortfolioIdOrderByTransactionDateAscIdAsc(
                                portfolioId
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

        PortfolioHistoryGranularity granularity =
                granularityResolver.resolve(
                        period,
                        dateRange.startDate(),
                        dateRange.endDate()
                );

        List<LocalDate> historyDates =
                dateGenerator.generate(
                        dateRange.startDate(),
                        dateRange.endDate(),
                        granularity
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
                        dateRange,
                        granularity
                );

        List<PortfolioHistoryPoint> points =
                historyDates.stream()
                        .map(date ->
                                calculateHistoryPoint(
                                        date,
                                        transactionsByAsset,
                                        quotesByAsset
                                )
                        )
                        .toList();

        return new PortfolioHistoryResponse(
                portfolioId,
                period,
                dateRange.startDate(),
                dateRange.endDate(),
                granularity,
                points
        );
    }

    private Map<Asset, List<HistoricalQuote>> loadHistoricalQuotes(
            Map<Asset, List<Transaction>> transactionsByAsset,
            PortfolioHistoryDateRange dateRange,
            PortfolioHistoryGranularity granularity) {

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
                                                        granularity
                                                )
                        )
                );
    }

    private PortfolioHistoryPoint calculateHistoryPoint(
            LocalDate date,
            Map<Asset, List<Transaction>> transactionsByAsset,
            Map<Asset, List<HistoricalQuote>> quotesByAsset) {

        BigDecimal portfolioValue =
                BigDecimal.ZERO;

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
                continue;
            }

            BigDecimal assetValue =
                    quantity.multiply(
                            closePrice
                    );

            portfolioValue =
                    portfolioValue.add(
                            assetValue
                    );
        }

        return new PortfolioHistoryPoint(
                date,
                portfolioValue
        );
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
                        () -> new PortfolioNotFoundException(
                                portfolioId
                        )
                );
    }
}