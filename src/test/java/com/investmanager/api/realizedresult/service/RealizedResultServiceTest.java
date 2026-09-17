package com.investmanager.api.realizedresult.service;

import com.investmanager.api.asset.Asset;
import com.investmanager.api.portfolio.Portfolio;
import com.investmanager.api.portfolio.repository.PortfolioRepository;
import com.investmanager.api.realizedresult.dto.RealizedResultResponse;
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
import static org.mockito.Mockito.*;

class RealizedResultServiceTest {

    private RealizedResultService realizedResultService;

    private TransactionRepository transactionRepository;
    private PortfolioRepository portfolioRepository;

    private Asset asset;

    @BeforeEach
    void setUp() {

        transactionRepository =
                mock(TransactionRepository.class);

        portfolioRepository =
                mock(PortfolioRepository.class);

        realizedResultService =
                new RealizedResultService(
                        transactionRepository,
                        portfolioRepository
                );

        asset = mock(Asset.class);

        when(asset.getId())
                .thenReturn(1L);

        when(asset.getTicker())
                .thenReturn("ITUB4");
    }

    @Test
    void shouldCalculateRealizedProfitOnPartialSale() {

        Transaction buy = new Transaction(
                null,
                asset,
                TransactionType.BUY,
                new BigDecimal("100"),
                new BigDecimal("30.00"),
                LocalDate.of(2026, 9, 1)
        );

        Transaction sell = new Transaction(
                null,
                asset,
                TransactionType.SELL,
                new BigDecimal("40"),
                new BigDecimal("40.00"),
                LocalDate.of(2026, 9, 2)
        );

        List<Transaction> transactions =
                List.of(
                        buy,
                        sell
                );

        RealizedResultResponse result =
                realizedResultService
                        .calculateRealizedResult(
                                asset,
                                transactions
                        );

        assertEquals(
                0,
                result.soldQuantity()
                        .compareTo(
                                new BigDecimal("40")
                        )
        );

        assertEquals(
                0,
                result.totalSaleValue()
                        .compareTo(
                                new BigDecimal("1600.00")
                        )
        );

        assertEquals(
                0,
                result.totalSoldCost()
                        .compareTo(
                                new BigDecimal("1200.00")
                        )
        );

        assertEquals(
                0,
                result.realizedProfitLoss()
                        .compareTo(
                                new BigDecimal("400.00")
                        )
        );
    }

    @Test
    void shouldCalculateRealizedProfitUsingAveragePrice() {

        Transaction firstBuy = new Transaction(
                null,
                asset,
                TransactionType.BUY,
                new BigDecimal("100"),
                new BigDecimal("30.00"),
                LocalDate.of(2026, 9, 1)
        );

        Transaction secondBuy = new Transaction(
                null,
                asset,
                TransactionType.BUY,
                new BigDecimal("100"),
                new BigDecimal("40.00"),
                LocalDate.of(2026, 9, 2)
        );

        Transaction sell = new Transaction(
                null,
                asset,
                TransactionType.SELL,
                new BigDecimal("50"),
                new BigDecimal("45.00"),
                LocalDate.of(2026, 9, 3)
        );

        List<Transaction> transactions =
                List.of(
                        firstBuy,
                        secondBuy,
                        sell
                );

        RealizedResultResponse result =
                realizedResultService
                        .calculateRealizedResult(
                                asset,
                                transactions
                        );

        assertEquals(
                0,
                result.soldQuantity()
                        .compareTo(
                                new BigDecimal("50")
                        )
        );

        assertEquals(
                0,
                result.totalSaleValue()
                        .compareTo(
                                new BigDecimal("2250.00")
                        )
        );

        assertEquals(
                0,
                result.totalSoldCost()
                        .compareTo(
                                new BigDecimal("1750.00")
                        )
        );

        assertEquals(
                0,
                result.realizedProfitLoss()
                        .compareTo(
                                new BigDecimal("500.00")
                        )
        );
    }

    @Test
    void shouldCalculateRealizedLossUsingAveragePrice() {

        Transaction firstBuy = new Transaction(
                null,
                asset,
                TransactionType.BUY,
                new BigDecimal("100"),
                new BigDecimal("30.00"),
                LocalDate.of(2026, 9, 1)
        );

        Transaction secondBuy = new Transaction(
                null,
                asset,
                TransactionType.BUY,
                new BigDecimal("100"),
                new BigDecimal("40.00"),
                LocalDate.of(2026, 9, 2)
        );

        Transaction sell = new Transaction(
                null,
                asset,
                TransactionType.SELL,
                new BigDecimal("50"),
                new BigDecimal("25.00"),
                LocalDate.of(2026, 9, 3)
        );

        List<Transaction> transactions =
                List.of(
                        firstBuy,
                        secondBuy,
                        sell
                );

        RealizedResultResponse result =
                realizedResultService
                        .calculateRealizedResult(
                                asset,
                                transactions
                        );

        assertEquals(
                0,
                result.soldQuantity()
                        .compareTo(
                                new BigDecimal("50")
                        )
        );

        assertEquals(
                0,
                result.totalSaleValue()
                        .compareTo(
                                new BigDecimal("1250.00")
                        )
        );

        assertEquals(
                0,
                result.totalSoldCost()
                        .compareTo(
                                new BigDecimal("1750.00")
                        )
        );

        assertEquals(
                0,
                result.realizedProfitLoss()
                        .compareTo(
                                new BigDecimal("-500.00")
                        )
        );
    }

    @Test
    void shouldResetAveragePriceAfterFullSaleAndRebuy() {

        Transaction firstBuy = new Transaction(
                null,
                asset,
                TransactionType.BUY,
                new BigDecimal("100"),
                new BigDecimal("30.00"),
                LocalDate.of(2026, 9, 1)
        );

        Transaction fullSale = new Transaction(
                null,
                asset,
                TransactionType.SELL,
                new BigDecimal("100"),
                new BigDecimal("40.00"),
                LocalDate.of(2026, 9, 2)
        );

        Transaction secondBuy = new Transaction(
                null,
                asset,
                TransactionType.BUY,
                new BigDecimal("50"),
                new BigDecimal("50.00"),
                LocalDate.of(2026, 9, 3)
        );

        Transaction partialSale = new Transaction(
                null,
                asset,
                TransactionType.SELL,
                new BigDecimal("20"),
                new BigDecimal("60.00"),
                LocalDate.of(2026, 9, 4)
        );

        List<Transaction> transactions =
                List.of(
                        firstBuy,
                        fullSale,
                        secondBuy,
                        partialSale
                );

        RealizedResultResponse result =
                realizedResultService
                        .calculateRealizedResult(
                                asset,
                                transactions
                        );

        assertEquals(
                0,
                result.soldQuantity()
                        .compareTo(
                                new BigDecimal("120")
                        )
        );

        assertEquals(
                0,
                result.totalSaleValue()
                        .compareTo(
                                new BigDecimal("5200.00")
                        )
        );

        assertEquals(
                0,
                result.totalSoldCost()
                        .compareTo(
                                new BigDecimal("4000.00")
                        )
        );

        assertEquals(
                0,
                result.realizedProfitLoss()
                        .compareTo(
                                new BigDecimal("1200.00")
                        )
        );
    }

    @Test
    void shouldCalculateRealizedResultsFromPortfolioTransactions() {

        Long portfolioId = 6L;
        Long userId = 1L;

        Portfolio portfolio =
                mock(Portfolio.class);

        when(
                portfolioRepository
                        .findByIdAndUserId(
                                portfolioId,
                                userId
                        )
        ).thenReturn(
                Optional.of(portfolio)
        );

        Transaction buy = new Transaction(
                portfolio,
                asset,
                TransactionType.BUY,
                new BigDecimal("100"),
                new BigDecimal("30.00"),
                LocalDate.of(2026, 9, 1)
        );

        Transaction sell = new Transaction(
                portfolio,
                asset,
                TransactionType.SELL,
                new BigDecimal("40"),
                new BigDecimal("40.00"),
                LocalDate.of(2026, 9, 2)
        );

        when(
                transactionRepository
                        .findByPortfolioIdAndPortfolioUserIdOrderByTransactionDateAscIdAsc(
                                portfolioId,
                                userId
                        )
        ).thenReturn(
                List.of(
                        buy,
                        sell
                )
        );

        List<RealizedResultResponse> results =
                realizedResultService
                        .calculateRealizedResults(
                                portfolioId,
                                userId
                        );

        assertEquals(
                1,
                results.size()
        );

        RealizedResultResponse result =
                results.getFirst();

        assertEquals(
                "ITUB4",
                result.assetTicker()
        );

        assertEquals(
                0,
                result.realizedProfitLoss()
                        .compareTo(
                                new BigDecimal("400.00")
                        )
        );

        verify(
                portfolioRepository
        ).findByIdAndUserId(
                portfolioId,
                userId
        );

        verify(
                transactionRepository
        ).findByPortfolioIdAndPortfolioUserIdOrderByTransactionDateAscIdAsc(
                portfolioId,
                userId
        );
    }
}