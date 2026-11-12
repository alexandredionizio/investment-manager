package com.investmanager.api.portfoliohistory.cash;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static com.investmanager.api.portfoliohistory.cash.CashLedgerCalculator.*;

class CashLedgerCalculatorTest {
    private final CashLedgerCalculator calculator = new CashLedgerCalculator();
    private static final LocalDate START = LocalDate.of(2026, 1, 1);
    private static Entry e(int day, Kind kind, String amount) {
        return new Entry(START.plusDays(day), kind, new BigDecimal(amount));
    }
    private Result compute(String opening, int asOfDay, Entry... events) {
        return calculator.calculate(Optional.of(new BigDecimal(opening)), START,
                START.plusDays(asOfDay), List.of(events));
    }
    @Test void missingVerifiedOpeningBalanceIsUnknown() {
        Result result = calculator.calculate(Optional.empty(), START, START.plusDays(10),
                List.of(e(1, Kind.CONTRIBUTION, "100")));
        assertEquals(Status.OPENING_BALANCE_UNKNOWN, result.status());
        assertTrue(result.balance().isEmpty());
    }
    @Test void contributionAndBuyReduceOnlyCash() {
        Result r = compute("0", 2, e(1, Kind.CONTRIBUTION, "100"), e(2, Kind.BUY, "60"));
        assertEquals(Status.COMPLETE, r.status());
        assertEquals(0, r.balance().orElseThrow().compareTo(new BigDecimal("40")));
    }
    @Test void saleAndPaidDividendIncreaseCash() {
        Result r = compute("10", 3, e(1, Kind.SELL, "40"), e(3, Kind.RECEIVED_INCOME, "2.50"));
        assertEquals(0, r.balance().orElseThrow().compareTo(new BigDecimal("52.50")));
    }
    @Test void withdrawalDebitsCash() {
        Result r = compute("100", 1, e(1, Kind.WITHDRAWAL, "25"));
        assertEquals(0, r.balance().orElseThrow().compareTo(new BigDecimal("75")));
    }
    @Test void negativeCashMarksEntireHistoryUnreconciled() {
        Result r = compute("0", 3, e(1, Kind.BUY, "20"), e(3, Kind.CONTRIBUTION, "100"));
        assertEquals(Status.NEGATIVE_CASH, r.status());
        assertEquals(START.plusDays(1), r.firstDeficitDate());
        assertTrue(r.balance().isEmpty());
    }
    @Test void futureEventsAreNotCounted() {
        Result r = compute("10", 1, e(2, Kind.BUY, "100"));
        assertEquals(Status.COMPLETE, r.status());
        assertEquals(0, r.balance().orElseThrow().compareTo(new BigDecimal("10")));
    }
    @Test void sameDayNettingAvoidsFabricatedIntradayOrdering() {
        Result r = compute("0", 1, e(1, Kind.BUY, "100"), e(1, Kind.CONTRIBUTION, "100"));
        assertEquals(Status.COMPLETE, r.status());
        assertEquals(0, r.balance().orElseThrow().compareTo(BigDecimal.ZERO));
    }
    @Test void earlierThanBaselineEventsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> compute("100", 1,
                new Entry(START.minusDays(1), Kind.BUY, BigDecimal.ONE)));
    }
    @Test void invalidAmountsRejected() {
        assertThrows(IllegalArgumentException.class, () -> e(1, Kind.BUY, "0"));
        assertThrows(IllegalArgumentException.class, () -> e(1, Kind.BUY, "-1"));
    }
    @Test void asOfBeforeOpeningRejected() {
        assertThrows(IllegalArgumentException.class, () -> compute("0", -1));
    }
}
