package com.investmanager.api.position.service;

import com.investmanager.api.asset.Asset;
import com.investmanager.api.portfolio.exception.PortfolioNotFoundException;
import com.investmanager.api.portfolio.repository.PortfolioRepository;
import com.investmanager.api.position.dto.PositionResponse;
import com.investmanager.api.position.exception.InsufficientPositionException;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PositionServiceTest {

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private PortfolioRepository portfolioRepository;

    private PositionService positionService;

    @BeforeEach
    void setUp() {
        positionService = new PositionService(
                transactionRepository,
                portfolioRepository
        );
    }

    @Test
    void shouldCalculateAveragePriceForMultipleBuys() {

        Asset asset = new Asset();
        asset.setTicker("ITUB4");

        Transaction buy1 = new Transaction(
                null,
                asset,
                TransactionType.BUY,
                new BigDecimal("100"),
                new BigDecimal("35.50"),
                LocalDate.of(2026, 9, 1)
        );

        Transaction buy2 = new Transaction(
                null,
                asset,
                TransactionType.BUY,
                new BigDecimal("50"),
                new BigDecimal("41.50"),
                LocalDate.of(2026, 9, 2)
        );

        when(portfolioRepository.existsById(1L))
                .thenReturn(true);

        when(transactionRepository
                .findByPortfolioIdOrderByTransactionDateAscIdAsc(1L))
                .thenReturn(List.of(buy1, buy2));

        List<PositionResponse> positions =
                positionService.calculatePositions(1L);

        PositionResponse position = positions.getFirst();

        assertEquals(0,
                new BigDecimal("150").compareTo(position.quantity()));

        assertEquals(0,
                new BigDecimal("37.50").compareTo(position.averagePrice()));

        assertEquals(0,
                new BigDecimal("5625.00").compareTo(position.totalCost()));
    }

    @Test
    void shouldKeepAveragePriceAfterPartialSell() {

        Asset asset = new Asset();
        asset.setTicker("ITUB4");

        Transaction buy1 = new Transaction(
                null,
                asset,
                TransactionType.BUY,
                new BigDecimal("100"),
                new BigDecimal("35.50"),
                LocalDate.of(2026, 9, 1)
        );

        Transaction buy2 = new Transaction(
                null,
                asset,
                TransactionType.BUY,
                new BigDecimal("50"),
                new BigDecimal("41.50"),
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

        when(portfolioRepository.existsById(1L))
                .thenReturn(true);

        when(transactionRepository
                .findByPortfolioIdOrderByTransactionDateAscIdAsc(1L))
                .thenReturn(List.of(buy1, buy2, sell));

        List<PositionResponse> positions =
                positionService.calculatePositions(1L);

        PositionResponse position = positions.getFirst();

        assertEquals(
                0,
                new BigDecimal("100").compareTo(position.quantity())
        );

        assertEquals(
                0,
                new BigDecimal("37.50").compareTo(position.averagePrice())
        );

        assertEquals(
                0,
                new BigDecimal("3750.00").compareTo(position.totalCost())
        );
    }

    @Test
    void shouldResetPositionAfterFullSell() {

        Asset asset = new Asset();
        asset.setTicker("ITUB4");

        Transaction buy = new Transaction(
                null,
                asset,
                TransactionType.BUY,
                new BigDecimal("100"),
                new BigDecimal("35.50"),
                LocalDate.of(2026, 9, 1)
        );

        Transaction sell = new Transaction(
                null,
                asset,
                TransactionType.SELL,
                new BigDecimal("100"),
                new BigDecimal("45.00"),
                LocalDate.of(2026, 9, 2)
        );

        when(portfolioRepository.existsById(1L))
                .thenReturn(true);

        when(transactionRepository
                .findByPortfolioIdOrderByTransactionDateAscIdAsc(1L))
                .thenReturn(List.of(buy, sell));

        List<PositionResponse> positions =
                positionService.calculatePositions(1L);

        PositionResponse position = positions.getFirst();

        assertEquals(
                0,
                BigDecimal.ZERO.compareTo(position.quantity())
        );

        assertEquals(
                0,
                BigDecimal.ZERO.compareTo(position.averagePrice())
        );

        assertEquals(
                0,
                BigDecimal.ZERO.compareTo(position.totalCost())
        );
    }

    @Test
    void shouldThrowExceptionWhenSellExceedsAvailablePosition() {

        Asset asset = new Asset();
        asset.setTicker("ITUB4");

        Transaction buy = new Transaction(
                null,
                asset,
                TransactionType.BUY,
                new BigDecimal("100"),
                new BigDecimal("35.50"),
                LocalDate.of(2026, 9, 1)
        );

        Transaction sell = new Transaction(
                null,
                asset,
                TransactionType.SELL,
                new BigDecimal("150"),
                new BigDecimal("45.00"),
                LocalDate.of(2026, 9, 2)
        );

        when(portfolioRepository.existsById(1L))
                .thenReturn(true);

        when(transactionRepository
                .findByPortfolioIdOrderByTransactionDateAscIdAsc(1L))
                .thenReturn(List.of(buy, sell));

        assertThrows(
                InsufficientPositionException.class,
                () -> positionService.calculatePositions(1L)
        );
    }

    @Test
    void shouldThrowExceptionWhenPortfolioDoesNotExist() {

        when(portfolioRepository.existsById(999L))
                .thenReturn(false);

        assertThrows(
                PortfolioNotFoundException.class,
                () -> positionService.calculatePositions(999L)
        );
    }
}