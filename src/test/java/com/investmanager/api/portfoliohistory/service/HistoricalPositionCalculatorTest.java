package com.investmanager.api.portfoliohistory.service;

import com.investmanager.api.asset.Asset;
import com.investmanager.api.portfolio.Portfolio;
import com.investmanager.api.transaction.Transaction;
import com.investmanager.api.transaction.TransactionType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class HistoricalPositionCalculatorTest {

    private HistoricalPositionCalculator calculator;

    private Portfolio portfolio;
    private Asset asset;

    @BeforeEach
    void setUp() {

        calculator =
                new HistoricalPositionCalculator();

        portfolio =
                new Portfolio();

        asset =
                new Asset();

        asset.setTicker("ITUB4");
    }

    @Test
    void shouldCalculateQuantityAfterBuy() {

        Transaction buy =
                createTransaction(
                        TransactionType.BUY,
                        "100",
                        LocalDate.of(2026, 9, 14)
                );

        BigDecimal quantity =
                calculator.calculateQuantity(
                        List.of(buy),
                        LocalDate.of(2026, 9, 14)
                );

        assertEquals(
                0,
                new BigDecimal("100")
                        .compareTo(quantity)
        );
    }

    @Test
    void shouldCalculateQuantityAfterMultipleBuys() {

        Transaction buy1 =
                createTransaction(
                        TransactionType.BUY,
                        "100",
                        LocalDate.of(2026, 9, 14)
                );

        Transaction buy2 =
                createTransaction(
                        TransactionType.BUY,
                        "50",
                        LocalDate.of(2026, 9, 15)
                );

        BigDecimal quantity =
                calculator.calculateQuantity(
                        List.of(buy1, buy2),
                        LocalDate.of(2026, 9, 15)
                );

        assertEquals(
                0,
                new BigDecimal("150")
                        .compareTo(quantity)
        );
    }

    @Test
    void shouldSubtractQuantityAfterSell() {

        Transaction buy =
                createTransaction(
                        TransactionType.BUY,
                        "100",
                        LocalDate.of(2026, 9, 14)
                );

        Transaction sell =
                createTransaction(
                        TransactionType.SELL,
                        "40",
                        LocalDate.of(2026, 9, 15)
                );

        BigDecimal quantity =
                calculator.calculateQuantity(
                        List.of(buy, sell),
                        LocalDate.of(2026, 9, 15)
                );

        assertEquals(
                0,
                new BigDecimal("60")
                        .compareTo(quantity)
        );
    }

    @Test
    void shouldReturnZeroAfterFullSell() {

        Transaction buy =
                createTransaction(
                        TransactionType.BUY,
                        "100",
                        LocalDate.of(2026, 9, 14)
                );

        Transaction sell =
                createTransaction(
                        TransactionType.SELL,
                        "100",
                        LocalDate.of(2026, 9, 15)
                );

        BigDecimal quantity =
                calculator.calculateQuantity(
                        List.of(buy, sell),
                        LocalDate.of(2026, 9, 15)
                );

        assertEquals(
                0,
                BigDecimal.ZERO.compareTo(quantity)
        );
    }

    @Test
    void shouldIgnoreTransactionsAfterRequestedDate() {

        Transaction buy1 =
                createTransaction(
                        TransactionType.BUY,
                        "100",
                        LocalDate.of(2026, 9, 14)
                );

        Transaction buy2 =
                createTransaction(
                        TransactionType.BUY,
                        "50",
                        LocalDate.of(2026, 9, 16)
                );

        BigDecimal quantity =
                calculator.calculateQuantity(
                        List.of(buy1, buy2),
                        LocalDate.of(2026, 9, 15)
                );

        assertEquals(
                0,
                new BigDecimal("100")
                        .compareTo(quantity)
        );
    }

    @Test
    void shouldIncludeTransactionsOnRequestedDate() {

        Transaction buy =
                createTransaction(
                        TransactionType.BUY,
                        "100",
                        LocalDate.of(2026, 9, 15)
                );

        BigDecimal quantity =
                calculator.calculateQuantity(
                        List.of(buy),
                        LocalDate.of(2026, 9, 15)
                );

        assertEquals(
                0,
                new BigDecimal("100")
                        .compareTo(quantity)
        );
    }

    @Test
    void shouldReturnZeroWhenThereAreNoTransactions() {

        BigDecimal quantity =
                calculator.calculateQuantity(
                        List.of(),
                        LocalDate.of(2026, 9, 15)
                );

        assertEquals(
                0,
                BigDecimal.ZERO.compareTo(quantity)
        );
    }

    @Test
    void shouldThrowExceptionWhenTransactionsAreNull() {

        assertThrows(
                IllegalArgumentException.class,
                () -> calculator.calculateQuantity(
                        null,
                        LocalDate.of(2026, 9, 15)
                )
        );
    }

    @Test
    void shouldThrowExceptionWhenDateIsNull() {

        assertThrows(
                IllegalArgumentException.class,
                () -> calculator.calculateQuantity(
                        List.of(),
                        null
                )
        );
    }

    private Transaction createTransaction(
            TransactionType type,
            String quantity,
            LocalDate date) {

        return new Transaction(
                portfolio,
                asset,
                type,
                new BigDecimal(quantity),
                new BigDecimal("35.00"),
                date
        );
    }
}