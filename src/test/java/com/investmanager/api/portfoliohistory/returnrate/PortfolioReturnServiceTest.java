package com.investmanager.api.portfoliohistory.returnrate;

import com.investmanager.api.asset.Asset;
import com.investmanager.api.income.Income;
import com.investmanager.api.income.IncomeType;
import com.investmanager.api.income.repository.IncomeRepository;
import com.investmanager.api.portfolio.Portfolio;
import com.investmanager.api.portfolio.exception.PortfolioNotFoundException;
import com.investmanager.api.portfolio.repository.PortfolioRepository;
import com.investmanager.api.portfoliohistory.model.PortfolioHistoryDateRange;
import com.investmanager.api.portfoliohistory.model.PortfolioHistoryGranularity;
import com.investmanager.api.portfoliohistory.model.PortfolioHistoryPeriod;
import com.investmanager.api.portfoliohistory.returnrate.PortfolioHistoricalValueCalculator;
import com.investmanager.api.portfoliohistory.returnrate.PortfolioReturnService;
import com.investmanager.api.portfoliohistory.service.HistoricalPositionCalculator;
import com.investmanager.api.portfoliohistory.service.HistoricalQuoteResolver;
import com.investmanager.api.portfoliohistory.service.PortfolioHistoryDateGenerator;
import com.investmanager.api.portfoliohistory.service.PortfolioHistoryDateRangeResolver;
import com.investmanager.api.quote.model.HistoricalQuote;
import com.investmanager.api.quote.service.HistoricalQuoteService;
import com.investmanager.api.transaction.Transaction;
import com.investmanager.api.transaction.TransactionType;
import com.investmanager.api.transaction.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PortfolioReturnServiceTest {

    private PortfolioRepository portfolioRepository;
    private TransactionRepository transactionRepository;
    private IncomeRepository incomeRepository;
    private HistoricalQuoteService historicalQuoteService;
    private PortfolioHistoryDateGenerator dateGenerator;
    private PortfolioHistoryDateRangeResolver dateRangeResolver;
    private HistoricalPositionCalculator positionCalculator;
    private HistoricalQuoteResolver quoteResolver;
    private PortfolioHistoricalValueCalculator historicalValueCalculator;

    private PortfolioReturnService service;

    @BeforeEach
    void setUp() {
        portfolioRepository = mock(PortfolioRepository.class);
        transactionRepository = mock(TransactionRepository.class);
        incomeRepository = mock(IncomeRepository.class);
        historicalQuoteService = mock(HistoricalQuoteService.class);
        dateGenerator = mock(PortfolioHistoryDateGenerator.class);
        dateRangeResolver = mock(PortfolioHistoryDateRangeResolver.class);

        positionCalculator =
                new HistoricalPositionCalculator();

        quoteResolver =
                new HistoricalQuoteResolver();

        historicalValueCalculator =
                mock(PortfolioHistoricalValueCalculator.class);

        service = new PortfolioReturnService(
                portfolioRepository,
                transactionRepository,
                incomeRepository,
                historicalQuoteService,
                dateGenerator,
                dateRangeResolver,
                positionCalculator,
                quoteResolver,
                historicalValueCalculator
        );
    }

    @Test
    void shouldNeutralizePurchaseWithoutMarketReturn() {
        Long portfolioId = 1L;
        Long userId = 10L;
        LocalDate date = LocalDate.of(2026, 9, 21);

        Portfolio portfolio = new Portfolio();
        Asset asset = createAsset("ITUB4");

        Transaction purchase = new Transaction(
                portfolio,
                asset,
                TransactionType.BUY,
                new BigDecimal("100"),
                new BigDecimal("10.00"),
                date
        );

        prepareCommonScenario(
                portfolioId,
                userId,
                portfolio,
                asset,
                List.of(purchase),
                date,
                date
        );

        when(historicalValueCalculator.calculate(
                eq(date.minusDays(1)),
                anyMap(),
                anyMap()
        )).thenReturn(BigDecimal.ZERO);

        when(historicalValueCalculator.calculate(
                eq(date),
                anyMap(),
                anyMap()
        )).thenReturn(new BigDecimal("1000.00"));

        PortfolioReturnResponse response =
                service.getReturns(
                        portfolioId,
                        userId,
                        PortfolioHistoryPeriod.CUSTOM,
                        date,
                        date,
                        null
                );

        assertBigDecimalEquals(
                "0.0000000000",
                response.totalReturn()
        );

        assertEquals(
                1,
                response.points().size()
        );

        assertBigDecimalEquals(
                "0.0000000000",
                response.points().getFirst().dailyReturn()
        );

        assertBigDecimalEquals(
                "0.0000000000",
                response.points().getFirst().cumulativeReturn()
        );
    }

    @Test
    void shouldCalculatePositiveMarketReturn() {
        Long portfolioId = 1L;
        Long userId = 10L;

        LocalDate purchaseDate =
                LocalDate.of(2026, 9, 20);

        LocalDate returnDate =
                LocalDate.of(2026, 9, 21);

        Portfolio portfolio = new Portfolio();
        Asset asset = createAsset("ITUB4");

        Transaction purchase = new Transaction(
                portfolio,
                asset,
                TransactionType.BUY,
                new BigDecimal("100"),
                new BigDecimal("10.00"),
                purchaseDate
        );

        prepareCommonScenario(
                portfolioId,
                userId,
                portfolio,
                asset,
                List.of(purchase),
                returnDate,
                returnDate
        );

        when(historicalValueCalculator.calculate(
                eq(returnDate.minusDays(1)),
                anyMap(),
                anyMap()
        )).thenReturn(new BigDecimal("1000.00"));

        when(historicalValueCalculator.calculate(
                eq(returnDate),
                anyMap(),
                anyMap()
        )).thenReturn(new BigDecimal("1100.00"));

        PortfolioReturnResponse response =
                service.getReturns(
                        portfolioId,
                        userId,
                        PortfolioHistoryPeriod.CUSTOM,
                        returnDate,
                        returnDate,
                        null
                );

        assertBigDecimalEquals(
                "0.1000000000",
                response.totalReturn()
        );

        assertBigDecimalEquals(
                "0.1000000000",
                response.points().getFirst().dailyReturn()
        );
    }

    @Test
    void shouldNeutralizeSaleWithoutMarketReturn() {
        Long portfolioId = 1L;
        Long userId = 10L;

        LocalDate purchaseDate =
                LocalDate.of(2026, 9, 20);

        LocalDate saleDate =
                LocalDate.of(2026, 9, 21);

        Portfolio portfolio = new Portfolio();
        Asset asset = createAsset("ITUB4");

        Transaction purchase = new Transaction(
                portfolio,
                asset,
                TransactionType.BUY,
                new BigDecimal("100"),
                new BigDecimal("10.00"),
                purchaseDate
        );

        Transaction sale = new Transaction(
                portfolio,
                asset,
                TransactionType.SELL,
                new BigDecimal("40"),
                new BigDecimal("10.00"),
                saleDate
        );

        prepareCommonScenario(
                portfolioId,
                userId,
                portfolio,
                asset,
                List.of(purchase, sale),
                saleDate,
                saleDate
        );

        when(historicalValueCalculator.calculate(
                eq(saleDate.minusDays(1)),
                anyMap(),
                anyMap()
        )).thenReturn(new BigDecimal("1000.00"));

        when(historicalValueCalculator.calculate(
                eq(saleDate),
                anyMap(),
                anyMap()
        )).thenReturn(new BigDecimal("600.00"));

        PortfolioReturnResponse response =
                service.getReturns(
                        portfolioId,
                        userId,
                        PortfolioHistoryPeriod.CUSTOM,
                        saleDate,
                        saleDate,
                        null
                );

        assertBigDecimalEquals(
                "0.0000000000",
                response.totalReturn()
        );

        assertBigDecimalEquals(
                "0.0000000000",
                response.points().getFirst().dailyReturn()
        );
    }

    @Test
    void shouldCompoundTwoPositiveDailyReturns() {
        Long portfolioId = 1L;
        Long userId = 10L;

        LocalDate purchaseDate =
                LocalDate.of(2026, 9, 19);

        LocalDate firstDate =
                LocalDate.of(2026, 9, 20);

        LocalDate secondDate =
                LocalDate.of(2026, 9, 21);

        Portfolio portfolio = new Portfolio();
        Asset asset = createAsset("ITUB4");

        Transaction purchase = new Transaction(
                portfolio,
                asset,
                TransactionType.BUY,
                new BigDecimal("100"),
                new BigDecimal("10.00"),
                purchaseDate
        );

        prepareCommonScenario(
                portfolioId,
                userId,
                portfolio,
                asset,
                List.of(purchase),
                firstDate,
                secondDate
        );

        when(dateGenerator.generate(
                firstDate,
                secondDate,
                PortfolioHistoryGranularity.DAILY
        )).thenReturn(
                List.of(
                        firstDate,
                        secondDate
                )
        );

        when(historicalValueCalculator.calculate(
                eq(firstDate.minusDays(1)),
                anyMap(),
                anyMap()
        )).thenReturn(new BigDecimal("1000.00"));

        when(historicalValueCalculator.calculate(
                eq(firstDate),
                anyMap(),
                anyMap()
        )).thenReturn(new BigDecimal("1100.00"));

        when(historicalValueCalculator.calculate(
                eq(secondDate),
                anyMap(),
                anyMap()
        )).thenReturn(new BigDecimal("1210.00"));

        PortfolioReturnResponse response =
                service.getReturns(
                        portfolioId,
                        userId,
                        PortfolioHistoryPeriod.CUSTOM,
                        firstDate,
                        secondDate,
                        null
                );

        assertEquals(
                2,
                response.points().size()
        );

        assertBigDecimalEquals(
                "0.1000000000",
                response.points().get(0).dailyReturn()
        );

        assertBigDecimalEquals(
                "0.1000000000",
                response.points().get(1).dailyReturn()
        );

        assertBigDecimalEquals(
                "0.2100000000",
                response.totalReturn()
        );

        assertBigDecimalEquals(
                "0.2100000000",
                response.points().get(1).cumulativeReturn()
        );
    }

    @Test
    void shouldIncludeIncomeInPortfolioReturn() {
        Long portfolioId = 1L;
        Long userId = 10L;

        LocalDate purchaseDate =
                LocalDate.of(2026, 9, 20);

        LocalDate paymentDate =
                LocalDate.of(2026, 9, 21);

        Portfolio portfolio = new Portfolio();
        Asset asset = createAsset("ITUB4");

        Transaction purchase = new Transaction(
                portfolio,
                asset,
                TransactionType.BUY,
                new BigDecimal("100"),
                new BigDecimal("10.00"),
                purchaseDate
        );

        Income income = new Income(
                portfolio,
                asset,
                IncomeType.DIVIDEND,
                new BigDecimal("2.00"),
                new BigDecimal("100"),
                purchaseDate,
                paymentDate
        );

        prepareCommonScenario(
                portfolioId,
                userId,
                portfolio,
                asset,
                List.of(purchase),
                paymentDate,
                paymentDate
        );

        when(incomeRepository
                .findByPortfolioIdAndPortfolioUserIdOrderByPaymentDateAscIdAsc(
                        portfolioId,
                        userId
                )
        ).thenReturn(
                List.of(income)
        );

        when(historicalValueCalculator.calculate(
                eq(paymentDate.minusDays(1)),
                anyMap(),
                anyMap()
        )).thenReturn(new BigDecimal("1000.00"));

        when(historicalValueCalculator.calculate(
                eq(paymentDate),
                anyMap(),
                anyMap()
        )).thenReturn(new BigDecimal("1000.00"));

        PortfolioReturnResponse response =
                service.getReturns(
                        portfolioId,
                        userId,
                        PortfolioHistoryPeriod.CUSTOM,
                        paymentDate,
                        paymentDate,
                        null
                );

        assertBigDecimalEquals(
                "0.2000000000",
                response.totalReturn()
        );

        assertBigDecimalEquals(
                "0.2000000000",
                response.points().getFirst().dailyReturn()
        );
    }

    @Test
    void shouldNeutralizePurchaseAndPreserveMarketReturn() {
        Long portfolioId = 1L;
        Long userId = 10L;

        LocalDate initialPurchaseDate =
                LocalDate.of(2026, 9, 20);

        LocalDate returnDate =
                LocalDate.of(2026, 9, 21);

        Portfolio portfolio = new Portfolio();
        Asset asset = createAsset("ITUB4");

        Transaction initialPurchase = new Transaction(
                portfolio,
                asset,
                TransactionType.BUY,
                new BigDecimal("100"),
                new BigDecimal("10.00"),
                initialPurchaseDate
        );

        Transaction additionalPurchase = new Transaction(
                portfolio,
                asset,
                TransactionType.BUY,
                new BigDecimal("50"),
                new BigDecimal("10.00"),
                returnDate
        );

        prepareCommonScenario(
                portfolioId,
                userId,
                portfolio,
                asset,
                List.of(
                        initialPurchase,
                        additionalPurchase
                ),
                returnDate,
                returnDate
        );

        when(historicalValueCalculator.calculate(
                eq(returnDate.minusDays(1)),
                anyMap(),
                anyMap()
        )).thenReturn(new BigDecimal("1000.00"));

        when(historicalValueCalculator.calculate(
                eq(returnDate),
                anyMap(),
                anyMap()
        )).thenReturn(new BigDecimal("1650.00"));

        PortfolioReturnResponse response =
                service.getReturns(
                        portfolioId,
                        userId,
                        PortfolioHistoryPeriod.CUSTOM,
                        returnDate,
                        returnDate,
                        null
                );

        assertBigDecimalEquals(
                "0.1000000000",
                response.totalReturn()
        );

        assertBigDecimalEquals(
                "0.1000000000",
                response.points().getFirst().dailyReturn()
        );
    }

    @Test
    void shouldNeutralizeSaleAndPreserveMarketReturn() {
        Long portfolioId = 1L;
        Long userId = 10L;

        LocalDate purchaseDate =
                LocalDate.of(2026, 9, 20);

        LocalDate saleDate =
                LocalDate.of(2026, 9, 21);

        Portfolio portfolio = new Portfolio();
        Asset asset = createAsset("ITUB4");

        Transaction purchase = new Transaction(
                portfolio,
                asset,
                TransactionType.BUY,
                new BigDecimal("100"),
                new BigDecimal("10.00"),
                purchaseDate
        );

        Transaction sale = new Transaction(
                portfolio,
                asset,
                TransactionType.SELL,
                new BigDecimal("40"),
                new BigDecimal("11.00"),
                saleDate
        );

        prepareCommonScenario(
                portfolioId,
                userId,
                portfolio,
                asset,
                List.of(
                        purchase,
                        sale
                ),
                saleDate,
                saleDate
        );

        when(historicalValueCalculator.calculate(
                eq(saleDate.minusDays(1)),
                anyMap(),
                anyMap()
        )).thenReturn(new BigDecimal("1000.00"));

        when(historicalValueCalculator.calculate(
                eq(saleDate),
                anyMap(),
                anyMap()
        )).thenReturn(new BigDecimal("660.00"));

        PortfolioReturnResponse response =
                service.getReturns(
                        portfolioId,
                        userId,
                        PortfolioHistoryPeriod.CUSTOM,
                        saleDate,
                        saleDate,
                        null
                );

        assertBigDecimalEquals(
                "0.1000000000",
                response.totalReturn()
        );

        assertBigDecimalEquals(
                "0.1000000000",
                response.points().getFirst().dailyReturn()
        );
    }

    @Test
    void shouldThrowPortfolioNotFoundWhenPortfolioDoesNotBelongToUser() {
        Long portfolioId = 1L;
        Long userId = 10L;

        when(portfolioRepository.findByIdAndUserId(
                portfolioId,
                userId
        )).thenReturn(
                Optional.empty()
        );

        assertThrows(
                PortfolioNotFoundException.class,
                () -> service.getReturns(
                        portfolioId,
                        userId,
                        PortfolioHistoryPeriod.ONE_YEAR,
                        null,
                        null,
                        LocalDate.of(2026, 9, 21)
                )
        );

        verify(portfolioRepository)
                .findByIdAndUserId(
                        portfolioId,
                        userId
                );
    }

    @Test
    void shouldThrowExceptionWhenHistoricalQuoteIsUnavailable() {
        Long portfolioId = 1L;
        Long userId = 10L;

        LocalDate purchaseDate =
                LocalDate.of(2026, 9, 19);

        LocalDate returnDate =
                LocalDate.of(2026, 9, 21);

        Portfolio portfolio = new Portfolio();
        Asset asset = createAsset("ITUB4");

        Transaction purchase = new Transaction(
                portfolio,
                asset,
                TransactionType.BUY,
                new BigDecimal("100"),
                new BigDecimal("10.00"),
                purchaseDate
        );

        when(portfolioRepository.findByIdAndUserId(
                portfolioId,
                userId
        )).thenReturn(
                Optional.of(portfolio)
        );

        when(transactionRepository
                .findByPortfolioIdAndPortfolioUserIdOrderByTransactionDateAscIdAsc(
                        portfolioId,
                        userId
                )
        ).thenReturn(
                List.of(purchase)
        );

        when(incomeRepository
                .findByPortfolioIdAndPortfolioUserIdOrderByPaymentDateAscIdAsc(
                        portfolioId,
                        userId
                )
        ).thenReturn(
                List.of()
        );

        when(dateRangeResolver.resolve(
                PortfolioHistoryPeriod.CUSTOM,
                null,
                returnDate,
                returnDate,
                purchaseDate
        )).thenReturn(
                new PortfolioHistoryDateRange(
                        returnDate,
                        returnDate
                )
        );

        when(dateGenerator.generate(
                returnDate,
                returnDate,
                PortfolioHistoryGranularity.DAILY
        )).thenReturn(
                List.of(returnDate)
        );

        /*
         * Existe uma cotação, mas somente em 21/09.
         *
         * Como já havia posição em 20/09, o cálculo precisa
         * conhecer uma cotação de 20/09 ou anterior.
         *
         * O sistema NÃO pode usar a cotação futura de 21/09
         * para valorar a carteira em 20/09.
         */
        when(historicalQuoteService.getHistoricalQuotes(
                asset.getTicker(),
                returnDate.minusDays(7),
                returnDate,
                PortfolioHistoryGranularity.DAILY
        )).thenReturn(
                List.of(
                        new HistoricalQuote(
                                returnDate,
                                new BigDecimal("11.00")
                        )
                )
        );

        HistoricalQuoteUnavailableException exception =
                assertThrows(
                        HistoricalQuoteUnavailableException.class,
                        () -> service.getReturns(
                                portfolioId,
                                userId,
                                PortfolioHistoryPeriod.CUSTOM,
                                returnDate,
                                returnDate,
                                null
                        )
                );

        assertEquals(
                "Cotação histórica indisponível para o ativo ITUB4 "
                        + "na data 2026-09-20 ou em data anterior.",
                exception.getMessage()
        );
    }

    @Test
    void shouldUseZeroReturnOnPortfolioFirstDay() {
        Long portfolioId = 1L;
        Long userId = 10L;
        LocalDate firstDate = LocalDate.of(2026, 9, 14);

        Portfolio portfolio = new Portfolio();
        Asset asset = createAsset("ITUB4");

        Transaction initialPurchase = new Transaction(
                portfolio,
                asset,
                TransactionType.BUY,
                new BigDecimal("100"),
                new BigDecimal("10.00"),
                firstDate
        );

        prepareCommonScenario(
                portfolioId,
                userId,
                portfolio,
                asset,
                List.of(initialPurchase),
                firstDate,
                firstDate
        );

        when(historicalValueCalculator.calculate(
                eq(firstDate.minusDays(1)),
                anyMap(),
                anyMap()
        )).thenReturn(BigDecimal.ZERO);

        when(historicalValueCalculator.calculate(
                eq(firstDate),
                anyMap(),
                anyMap()
        )).thenReturn(new BigDecimal("4235.00"));

        PortfolioReturnResponse response =
                service.getReturns(
                        portfolioId,
                        userId,
                        PortfolioHistoryPeriod.CUSTOM,
                        firstDate,
                        firstDate,
                        null
                );

        assertBigDecimalEquals(
                "0.0000000000",
                response.totalReturn()
        );

        assertBigDecimalEquals(
                "0.0000000000",
                response.points().getFirst().dailyReturn()
        );

        assertBigDecimalEquals(
                "0.0000000000",
                response.points().getFirst().cumulativeReturn()
        );
    }

    @Test
    void shouldStartReturnPeriodOnFirstTransactionDateWhenRequestedPeriodStartsEarlier() {
        Long portfolioId = 1L;
        Long userId = 10L;

        LocalDate requestedStartDate =
                LocalDate.of(2025, 9, 22);

        LocalDate firstTransactionDate =
                LocalDate.of(2026, 9, 14);

        LocalDate endDate =
                LocalDate.of(2026, 9, 22);

        Portfolio portfolio = new Portfolio();
        Asset asset = createAsset("ITUB4");

        Transaction initialPurchase = new Transaction(
                portfolio,
                asset,
                TransactionType.BUY,
                new BigDecimal("100"),
                new BigDecimal("10.00"),
                firstTransactionDate
        );

        when(portfolioRepository.findByIdAndUserId(
                portfolioId,
                userId
        )).thenReturn(
                Optional.of(portfolio)
        );

        when(transactionRepository
                .findByPortfolioIdAndPortfolioUserIdOrderByTransactionDateAscIdAsc(
                        portfolioId,
                        userId
                )
        ).thenReturn(
                List.of(initialPurchase)
        );

        when(incomeRepository
                .findByPortfolioIdAndPortfolioUserIdOrderByPaymentDateAscIdAsc(
                        portfolioId,
                        userId
                )
        ).thenReturn(
                List.of()
        );

        when(dateRangeResolver.resolve(
                PortfolioHistoryPeriod.ONE_YEAR,
                endDate,
                null,
                null,
                firstTransactionDate
        )).thenReturn(
                new PortfolioHistoryDateRange(
                        requestedStartDate,
                        endDate
                )
        );

        when(dateGenerator.generate(
                firstTransactionDate,
                endDate,
                PortfolioHistoryGranularity.DAILY
        )).thenReturn(
                List.of(firstTransactionDate)
        );

        when(historicalQuoteService.getHistoricalQuotes(
                asset.getTicker(),
                firstTransactionDate.minusDays(7),
                endDate,
                PortfolioHistoryGranularity.DAILY
        )).thenReturn(
                List.of(
                        new HistoricalQuote(
                                firstTransactionDate,
                                BigDecimal.ONE
                        )
                )
        );

        when(historicalValueCalculator.calculate(
                eq(firstTransactionDate.minusDays(1)),
                anyMap(),
                anyMap()
        )).thenReturn(BigDecimal.ZERO);

        when(historicalValueCalculator.calculate(
                eq(firstTransactionDate),
                anyMap(),
                anyMap()
        )).thenReturn(new BigDecimal("1000.00"));

        PortfolioReturnResponse response =
                service.getReturns(
                        portfolioId,
                        userId,
                        PortfolioHistoryPeriod.ONE_YEAR,
                        null,
                        null,
                        endDate
                );

        assertEquals(
                firstTransactionDate,
                response.startDate()
        );

        assertEquals(
                endDate,
                response.endDate()
        );

        assertEquals(
                1,
                response.points().size()
        );

        assertEquals(
                firstTransactionDate,
                response.points().getFirst().date()
        );

        assertBigDecimalEquals(
                "0.0000000000",
                response.totalReturn()
        );

        verify(dateGenerator).generate(
                firstTransactionDate,
                endDate,
                PortfolioHistoryGranularity.DAILY
        );

        verify(historicalQuoteService).getHistoricalQuotes(
                asset.getTicker(),
                firstTransactionDate.minusDays(7),
                endDate,
                PortfolioHistoryGranularity.DAILY
        );
    }

    private void prepareCommonScenario(
            Long portfolioId,
            Long userId,
            Portfolio portfolio,
            Asset asset,
            List<Transaction> transactions,
            LocalDate startDate,
            LocalDate endDate) {

        when(portfolioRepository.findByIdAndUserId(
                portfolioId,
                userId
        )).thenReturn(
                Optional.of(portfolio)
        );

        when(transactionRepository
                .findByPortfolioIdAndPortfolioUserIdOrderByTransactionDateAscIdAsc(
                        portfolioId,
                        userId
                )
        ).thenReturn(
                transactions
        );

        when(incomeRepository
                .findByPortfolioIdAndPortfolioUserIdOrderByPaymentDateAscIdAsc(
                        portfolioId,
                        userId
                )
        ).thenReturn(
                List.of()
        );

        LocalDate firstTransactionDate =
                transactions.getFirst()
                        .getTransactionDate();

        when(dateRangeResolver.resolve(
                PortfolioHistoryPeriod.CUSTOM,
                null,
                startDate,
                endDate,
                firstTransactionDate
        )).thenReturn(
                new PortfolioHistoryDateRange(
                        startDate,
                        endDate
                )
        );

        when(dateGenerator.generate(
                startDate,
                endDate,
                PortfolioHistoryGranularity.DAILY
        )).thenReturn(
                List.of(startDate)
        );

        when(historicalQuoteService.getHistoricalQuotes(
                asset.getTicker(),
                startDate.minusDays(7),
                endDate,
                PortfolioHistoryGranularity.DAILY
        )).thenReturn(
                List.of(
                        new HistoricalQuote(
                                startDate.minusDays(7),
                                BigDecimal.ONE
                        ),
                        new HistoricalQuote(
                                startDate,
                                BigDecimal.ONE
                        )
                )
        );
    }

    private Asset createAsset(String ticker) {
        Asset asset = new Asset();
        asset.setTicker(ticker);
        return asset;
    }

    private void assertBigDecimalEquals(
            String expected,
            BigDecimal actual) {

        assertEquals(
                0,
                actual.compareTo(
                        new BigDecimal(expected)
                )
        );
    }
}