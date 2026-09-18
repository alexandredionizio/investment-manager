package com.investmanager.api.portfoliohistory.service;

import com.investmanager.api.asset.Asset;
import com.investmanager.api.portfolio.Portfolio;
import com.investmanager.api.portfolio.exception.PortfolioNotFoundException;
import com.investmanager.api.portfolio.repository.PortfolioRepository;
import com.investmanager.api.portfoliohistory.dto.PortfolioHistoryResponse;
import com.investmanager.api.portfoliohistory.model.PortfolioHistoryGranularity;
import com.investmanager.api.portfoliohistory.model.PortfolioHistoryPeriod;
import com.investmanager.api.quote.model.HistoricalQuote;
import com.investmanager.api.quote.service.HistoricalQuoteService;
import com.investmanager.api.transaction.Transaction;
import com.investmanager.api.transaction.TransactionType;
import com.investmanager.api.transaction.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PortfolioHistoryServiceTest {

    private static final Long PORTFOLIO_ID = 1L;
    private static final Long USER_ID = 2L;

    @Mock
    private PortfolioRepository portfolioRepository;

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private HistoricalQuoteService historicalQuoteService;

    private PortfolioHistoryService portfolioHistoryService;

    @BeforeEach
    void setUp() {

        portfolioHistoryService =
                new PortfolioHistoryService(
                        portfolioRepository,
                        transactionRepository,
                        historicalQuoteService,
                        new PortfolioHistoryGranularityResolver(),
                        new PortfolioHistoryDateRangeResolver(),
                        new PortfolioHistoryDateGenerator(),
                        new HistoricalPositionCalculator(),
                        new HistoricalQuoteResolver()
                );
    }

    @Test
    void shouldResolveHistoryForOneYearPeriod() {

        Portfolio portfolio =
                new Portfolio();

        Asset asset =
                new Asset();

        asset.setTicker("ITUB4");

        Transaction transaction =
                new Transaction(
                        portfolio,
                        asset,
                        TransactionType.BUY,
                        new BigDecimal("100"),
                        new BigDecimal("35.00"),
                        LocalDate.of(2024, 1, 10)
                );

        when(portfolioRepository.findByIdAndUserId(
                PORTFOLIO_ID,
                USER_ID
        )).thenReturn(Optional.of(portfolio));

        when(transactionRepository
                .findByPortfolioIdOrderByTransactionDateAscIdAsc(
                        PORTFOLIO_ID
                ))
                .thenReturn(List.of(transaction));

        when(historicalQuoteService.getHistoricalQuotes(
                "ITUB4",
                LocalDate.of(2025, 9, 11),
                LocalDate.of(2026, 9, 18),
                PortfolioHistoryGranularity.WEEKLY
        )).thenReturn(
                List.of(
                        new HistoricalQuote(
                                LocalDate.of(2025, 9, 18),
                                new BigDecimal("40.00")
                        ),
                        new HistoricalQuote(
                                LocalDate.of(2026, 9, 18),
                                new BigDecimal("42.00")
                        )
                )
        );

        PortfolioHistoryResponse response =
                portfolioHistoryService.getHistory(
                        PORTFOLIO_ID,
                        USER_ID,
                        PortfolioHistoryPeriod.ONE_YEAR,
                        null,
                        null,
                        LocalDate.of(2026, 9, 18)
                );

        assertEquals(
                PORTFOLIO_ID,
                response.portfolioId()
        );

        assertEquals(
                PortfolioHistoryPeriod.ONE_YEAR,
                response.period()
        );

        assertEquals(
                LocalDate.of(2025, 9, 18),
                response.startDate()
        );

        assertEquals(
                LocalDate.of(2026, 9, 18),
                response.endDate()
        );

        assertEquals(
                PortfolioHistoryGranularity.WEEKLY,
                response.granularity()
        );

        assertFalse(
                response.points().isEmpty()
        );

        assertEquals(
                LocalDate.of(2025, 9, 18),
                response.points()
                        .getFirst()
                        .date()
        );

        assertEquals(
                0,
                new BigDecimal("4000.00")
                        .compareTo(
                                response.points()
                                        .getFirst()
                                        .value()
                        )
        );

        assertEquals(
                LocalDate.of(2026, 9, 18),
                response.points()
                        .getLast()
                        .date()
        );

        assertEquals(
                0,
                new BigDecimal("4200.00")
                        .compareTo(
                                response.points()
                                        .getLast()
                                        .value()
                        )
        );

        verify(portfolioRepository)
                .findByIdAndUserId(
                        PORTFOLIO_ID,
                        USER_ID
                );

        verify(transactionRepository)
                .findByPortfolioIdOrderByTransactionDateAscIdAsc(
                        PORTFOLIO_ID
                );

        verify(historicalQuoteService)
                .getHistoricalQuotes(
                        "ITUB4",
                        LocalDate.of(2025, 9, 11),
                        LocalDate.of(2026, 9, 18),
                        PortfolioHistoryGranularity.WEEKLY
                );
    }

    @Test
    void shouldResolveHistoryForCustomPeriod() {

        Portfolio portfolio =
                new Portfolio();

        Asset asset =
                new Asset();

        asset.setTicker("ITUB4");

        Transaction transaction =
                new Transaction(
                        portfolio,
                        asset,
                        TransactionType.BUY,
                        new BigDecimal("100"),
                        new BigDecimal("35.00"),
                        LocalDate.of(2026, 1, 1)
                );

        when(portfolioRepository.findByIdAndUserId(
                PORTFOLIO_ID,
                USER_ID
        )).thenReturn(Optional.of(portfolio));

        when(transactionRepository
                .findByPortfolioIdOrderByTransactionDateAscIdAsc(
                        PORTFOLIO_ID
                ))
                .thenReturn(List.of(transaction));

        when(historicalQuoteService.getHistoricalQuotes(
                "ITUB4",
                LocalDate.of(2026, 2, 22),
                LocalDate.of(2026, 9, 1),
                PortfolioHistoryGranularity.DAILY
        )).thenReturn(
                List.of(
                        new HistoricalQuote(
                                LocalDate.of(2026, 3, 1),
                                new BigDecimal("40.00")
                        )
                )
        );

        PortfolioHistoryResponse response =
                portfolioHistoryService.getHistory(
                        PORTFOLIO_ID,
                        USER_ID,
                        PortfolioHistoryPeriod.CUSTOM,
                        LocalDate.of(2026, 3, 1),
                        LocalDate.of(2026, 9, 1),
                        null
                );

        assertEquals(
                LocalDate.of(2026, 3, 1),
                response.startDate()
        );

        assertEquals(
                LocalDate.of(2026, 9, 1),
                response.endDate()
        );

        assertEquals(
                PortfolioHistoryGranularity.DAILY,
                response.granularity()
        );

        assertFalse(
                response.points().isEmpty()
        );

        assertEquals(
                0,
                new BigDecimal("4000.00")
                        .compareTo(
                                response.points()
                                        .getFirst()
                                        .value()
                        )
        );
    }

    @Test
    void shouldResolveAllPeriodUsingFirstTransactionDate() {

        Portfolio portfolio =
                new Portfolio();

        Asset asset =
                new Asset();

        asset.setTicker("ITUB4");

        Transaction firstTransaction =
                new Transaction(
                        portfolio,
                        asset,
                        TransactionType.BUY,
                        new BigDecimal("100"),
                        new BigDecimal("30.00"),
                        LocalDate.of(2023, 4, 10)
                );

        Transaction secondTransaction =
                new Transaction(
                        portfolio,
                        asset,
                        TransactionType.BUY,
                        new BigDecimal("50"),
                        new BigDecimal("35.00"),
                        LocalDate.of(2024, 5, 20)
                );

        when(portfolioRepository.findByIdAndUserId(
                PORTFOLIO_ID,
                USER_ID
        )).thenReturn(Optional.of(portfolio));

        when(transactionRepository
                .findByPortfolioIdOrderByTransactionDateAscIdAsc(
                        PORTFOLIO_ID
                ))
                .thenReturn(
                        List.of(
                                firstTransaction,
                                secondTransaction
                        )
                );

        when(historicalQuoteService.getHistoricalQuotes(
                "ITUB4",
                LocalDate.of(2023, 4, 3),
                LocalDate.of(2026, 9, 18),
                PortfolioHistoryGranularity.MONTHLY
        )).thenReturn(
                List.of(
                        new HistoricalQuote(
                                LocalDate.of(2023, 4, 10),
                                new BigDecimal("30.00")
                        )
                )
        );

        PortfolioHistoryResponse response =
                portfolioHistoryService.getHistory(
                        PORTFOLIO_ID,
                        USER_ID,
                        PortfolioHistoryPeriod.ALL,
                        null,
                        null,
                        LocalDate.of(2026, 9, 18)
                );

        assertEquals(
                LocalDate.of(2023, 4, 10),
                response.startDate()
        );

        assertEquals(
                LocalDate.of(2026, 9, 18),
                response.endDate()
        );

        assertEquals(
                PortfolioHistoryGranularity.MONTHLY,
                response.granularity()
        );

        assertFalse(
                response.points().isEmpty()
        );

        assertEquals(
                0,
                new BigDecimal("3000.00")
                        .compareTo(
                                response.points()
                                        .getFirst()
                                        .value()
                        )
        );
    }

    @Test
    void shouldSumHistoricalValueOfMultipleAssets() {

        Portfolio portfolio =
                new Portfolio();

        Asset itub4 =
                new Asset();

        itub4.setTicker("ITUB4");

        Asset petr4 =
                new Asset();

        petr4.setTicker("PETR4");

        Transaction itub4Buy =
                new Transaction(
                        portfolio,
                        itub4,
                        TransactionType.BUY,
                        new BigDecimal("100"),
                        new BigDecimal("35.00"),
                        LocalDate.of(2026, 8, 1)
                );

        Transaction petr4Buy =
                new Transaction(
                        portfolio,
                        petr4,
                        TransactionType.BUY,
                        new BigDecimal("50"),
                        new BigDecimal("25.00"),
                        LocalDate.of(2026, 8, 1)
                );

        when(portfolioRepository.findByIdAndUserId(
                PORTFOLIO_ID,
                USER_ID
        )).thenReturn(Optional.of(portfolio));

        when(transactionRepository
                .findByPortfolioIdOrderByTransactionDateAscIdAsc(
                        PORTFOLIO_ID
                ))
                .thenReturn(
                        List.of(
                                itub4Buy,
                                petr4Buy
                        )
                );

        when(historicalQuoteService.getHistoricalQuotes(
                "ITUB4",
                LocalDate.of(2026, 8, 11),
                LocalDate.of(2026, 9, 18),
                PortfolioHistoryGranularity.DAILY
        )).thenReturn(
                List.of(
                        new HistoricalQuote(
                                LocalDate.of(2026, 8, 18),
                                new BigDecimal("42.00")
                        )
                )
        );

        when(historicalQuoteService.getHistoricalQuotes(
                "PETR4",
                LocalDate.of(2026, 8, 11),
                LocalDate.of(2026, 9, 18),
                PortfolioHistoryGranularity.DAILY
        )).thenReturn(
                List.of(
                        new HistoricalQuote(
                                LocalDate.of(2026, 8, 18),
                                new BigDecimal("30.00")
                        )
                )
        );

        PortfolioHistoryResponse response =
                portfolioHistoryService.getHistory(
                        PORTFOLIO_ID,
                        USER_ID,
                        PortfolioHistoryPeriod.ONE_MONTH,
                        null,
                        null,
                        LocalDate.of(2026, 9, 18)
                );

        assertFalse(
                response.points().isEmpty()
        );

        assertEquals(
                0,
                new BigDecimal("5700.00")
                        .compareTo(
                                response.points()
                                        .getFirst()
                                        .value()
                        )
        );

        verify(historicalQuoteService)
                .getHistoricalQuotes(
                        "ITUB4",
                        LocalDate.of(2026, 8, 11),
                        LocalDate.of(2026, 9, 18),
                        PortfolioHistoryGranularity.DAILY
                );

        verify(historicalQuoteService)
                .getHistoricalQuotes(
                        "PETR4",
                        LocalDate.of(2026, 8, 11),
                        LocalDate.of(2026, 9, 18),
                        PortfolioHistoryGranularity.DAILY
                );
    }

    @Test
    void shouldUpdateHistoricalValueAfterSell() {

        Portfolio portfolio =
                new Portfolio();

        Asset asset =
                new Asset();

        asset.setTicker("ITUB4");

        Transaction buy =
                new Transaction(
                        portfolio,
                        asset,
                        TransactionType.BUY,
                        new BigDecimal("100"),
                        new BigDecimal("35.00"),
                        LocalDate.of(2026, 9, 1)
                );

        Transaction sell =
                new Transaction(
                        portfolio,
                        asset,
                        TransactionType.SELL,
                        new BigDecimal("40"),
                        new BigDecimal("45.00"),
                        LocalDate.of(2026, 9, 10)
                );

        when(portfolioRepository.findByIdAndUserId(
                PORTFOLIO_ID,
                USER_ID
        )).thenReturn(Optional.of(portfolio));

        when(transactionRepository
                .findByPortfolioIdOrderByTransactionDateAscIdAsc(
                        PORTFOLIO_ID
                ))
                .thenReturn(
                        List.of(
                                buy,
                                sell
                        )
                );

        when(historicalQuoteService.getHistoricalQuotes(
                "ITUB4",
                LocalDate.of(2026, 8, 25),
                LocalDate.of(2026, 9, 15),
                PortfolioHistoryGranularity.DAILY
        )).thenReturn(
                List.of(
                        new HistoricalQuote(
                                LocalDate.of(2026, 9, 1),
                                new BigDecimal("40.00")
                        ),
                        new HistoricalQuote(
                                LocalDate.of(2026, 9, 10),
                                new BigDecimal("42.00")
                        ),
                        new HistoricalQuote(
                                LocalDate.of(2026, 9, 15),
                                new BigDecimal("45.00")
                        )
                )
        );

        PortfolioHistoryResponse response =
                portfolioHistoryService.getHistory(
                        PORTFOLIO_ID,
                        USER_ID,
                        PortfolioHistoryPeriod.CUSTOM,
                        LocalDate.of(2026, 9, 1),
                        LocalDate.of(2026, 9, 15),
                        null
                );

        assertEquals(
                0,
                new BigDecimal("4000.00")
                        .compareTo(
                                response.points()
                                        .getFirst()
                                        .value()
                        )
        );

        assertEquals(
                0,
                new BigDecimal("2700.00")
                        .compareTo(
                                response.points()
                                        .getLast()
                                        .value()
                        )
        );
    }

    @Test
    void shouldThrowExceptionWhenPortfolioDoesNotBelongToUser() {

        when(portfolioRepository.findByIdAndUserId(
                PORTFOLIO_ID,
                USER_ID
        )).thenReturn(Optional.empty());

        assertThrows(
                PortfolioNotFoundException.class,
                () -> portfolioHistoryService.getHistory(
                        PORTFOLIO_ID,
                        USER_ID,
                        PortfolioHistoryPeriod.ONE_YEAR,
                        null,
                        null,
                        LocalDate.of(2026, 9, 18)
                )
        );

        verify(portfolioRepository)
                .findByIdAndUserId(
                        PORTFOLIO_ID,
                        USER_ID
                );

        verifyNoInteractions(
                transactionRepository
        );

        verifyNoInteractions(
                historicalQuoteService
        );
    }
}