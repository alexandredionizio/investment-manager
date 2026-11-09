package com.investmanager.api.transaction.validation;

import com.investmanager.api.asset.Asset;
import com.investmanager.api.portfolio.Portfolio;
import com.investmanager.api.position.exception.InsufficientPositionException;
import com.investmanager.api.transaction.Transaction;
import com.investmanager.api.transaction.TransactionType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static com.investmanager.api.transaction.TransactionType.BUY;
import static com.investmanager.api.transaction.TransactionType.SELL;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TransactionHistoryValidatorTest {

    private final TransactionHistoryValidator validator = new TransactionHistoryValidator();
    private final Portfolio portfolio = new Portfolio();
    private final Asset asset = new Asset();

    @Test
    void shouldRejectSaleWithoutHistory() {
        assertThrows(InsufficientPositionException.class,
                () -> validator.validateSale(List.of(), transaction(SELL, "50", 1)));
    }

    @Test
    void shouldRejectSaleBeforeFirstPurchase() {
        List<Transaction> history = List.of(transaction(BUY, "100", 10));

        assertThrows(InsufficientPositionException.class,
                () -> validator.validateSale(history, transaction(SELL, "50", 1)));
    }

    @Test
    void shouldAcceptValidRetroactiveSale() {
        List<Transaction> history = List.of(
                transaction(BUY, "100", 1),
                transaction(SELL, "20", 10));

        assertDoesNotThrow(() -> validator.validateSale(history, transaction(SELL, "50", 5)));
    }

    @Test
    void shouldRejectRetroactiveSaleThatInvalidatesLaterSale() {
        List<Transaction> history = List.of(
                transaction(BUY, "100", 1),
                transaction(SELL, "80", 10));

        assertThrows(InsufficientPositionException.class,
                () -> validator.validateSale(history, transaction(SELL, "50", 5)));
    }

    @Test
    void shouldRejectNegativeIntermediateBalanceDespitePositiveFinalBalance() {
        List<Transaction> history = List.of(
                transaction(BUY, "100", 1),
                transaction(SELL, "80", 10),
                transaction(BUY, "100", 20));

        assertThrows(InsufficientPositionException.class,
                () -> validator.validateSale(history, transaction(SELL, "50", 5)));
    }

    @Test
    void shouldAcceptSaleThatExactlyClosesPosition() {
        List<Transaction> history = List.of(
                transaction(BUY, "100", 1),
                transaction(SELL, "40", 2));

        assertDoesNotThrow(() -> validator.validateSale(history, transaction(SELL, "60", 3)));
    }

    @Test
    void shouldAcceptRetroactiveSaleThatLeavesZeroAfterLaterSale() {
        List<Transaction> history = List.of(
                transaction(BUY, "100", 1),
                transaction(SELL, "40", 10));

        assertDoesNotThrow(() -> validator.validateSale(history, transaction(SELL, "60", 5)));
    }

    @Test
    void shouldUseExactArithmeticForFractionalQuantities() {
        List<Transaction> history = List.of(
                transaction(BUY, "0.1", 1),
                transaction(BUY, "0.2", 2));

        assertDoesNotThrow(() -> validator.validateSale(history, transaction(SELL, "0.30000000", 3)));
    }

    @Test
    void shouldRejectFractionalDeficitWithoutRoundingItAway() {
        List<Transaction> history = List.of(transaction(BUY, "1.00000000", 1));

        assertThrows(InsufficientPositionException.class,
                () -> validator.validateSale(history, transaction(SELL, "1.00000001", 2)));
    }

    @Test
    void shouldApplyCandidateAfterAllExistingTransactionsOnSameDate() {
        List<Transaction> history = List.of(
                transaction(BUY, "100", 5),
                transaction(SELL, "80", 5),
                transaction(BUY, "30", 5),
                transaction(BUY, "10", 6));

        assertDoesNotThrow(() -> validator.validateSale(history, transaction(SELL, "50", 5)));
    }

    @Test
    void shouldNotUseNextDayPurchaseToCoverCandidateAfterSameDaySale() {
        List<Transaction> history = List.of(
                transaction(BUY, "100", 1),
                transaction(SELL, "100", 5),
                transaction(BUY, "100", 6));

        assertThrows(InsufficientPositionException.class,
                () -> validator.validateSale(history, transaction(SELL, "50", 5)));
    }

    @Test
    void shouldPreserveExistingOrderOnSameDateInsteadOfGroupingPurchasesFirst() {
        List<Transaction> history = List.of(
                transaction(SELL, "50", 5),
                transaction(BUY, "100", 5));

        assertThrows(InsufficientPositionException.class,
                () -> validator.validateSale(history, transaction(SELL, "10", 5)));
    }

    @Test
    void shouldRejectHistoryAlreadyInvalidBeforeCandidateDate() {
        List<Transaction> history = List.of(
                transaction(SELL, "50", 1),
                transaction(BUY, "100", 10));

        assertThrows(InsufficientPositionException.class,
                () -> validator.validateSale(history, transaction(SELL, "10", 20)));
    }

    @Test
    void shouldRejectHistoryAlreadyInvalidAfterCandidateDate() {
        List<Transaction> history = List.of(
                transaction(BUY, "100", 1),
                transaction(SELL, "150", 10),
                transaction(BUY, "100", 20));

        assertThrows(InsufficientPositionException.class,
                () -> validator.validateSale(history, transaction(SELL, "10", 5)));
    }

    private Transaction transaction(TransactionType type, String quantity, int day) {
        asset.setTicker("ITUB4");
        return new Transaction(portfolio, asset, type, new BigDecimal(quantity),
                BigDecimal.ONE, LocalDate.of(2026, 9, day));
    }
}
