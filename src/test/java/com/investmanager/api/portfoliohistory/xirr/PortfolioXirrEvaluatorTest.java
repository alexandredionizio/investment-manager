package com.investmanager.api.portfoliohistory.xirr;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class PortfolioXirrEvaluatorTest {
    private final PortfolioXirrEvaluator evaluator = new PortfolioXirrEvaluator();
    private static ExternalCapitalMovement transfer(String date, ExternalCapitalMovement.Kind kind, String amount) {
        return new ExternalCapitalMovement(LocalDate.parse(date), kind, new BigDecimal(amount));
    }
    private static final ExternalCapitalMovement.Kind IN = ExternalCapitalMovement.Kind.CONTRIBUTION;
    private static final ExternalCapitalMovement.Kind OUT = ExternalCapitalMovement.Kind.WITHDRAWAL;

    @Test void annualProfitFromContributionAndTerminalEquity() {
        var rate = evaluator.calculate(List.of(transfer("2025-01-01", IN, "1000")),
                LocalDate.parse("2026-01-01"), new BigDecimal("1100"), true);
        assertEquals(.10, rate.orElseThrow().doubleValue(), 1e-8);
    }
    @Test void annualLossFromContributionAndTerminalEquity() {
        var rate = evaluator.calculate(List.of(transfer("2025-01-01", IN, "1000")),
                LocalDate.parse("2026-01-01"), new BigDecimal("900"), true);
        assertEquals(-.10, rate.orElseThrow().doubleValue(), 1e-8);
    }
    @Test void withdrawalIsPositiveInvestorFlow() {
        var rate = evaluator.calculate(List.of(transfer("2025-01-01", IN, "1000"),
                transfer("2026-01-01", OUT, "200")), LocalDate.parse("2026-01-01"),
                new BigDecimal("900"), true);
        assertEquals(.10, rate.orElseThrow().doubleValue(), 1e-8);
    }
    @Test void additionalContributionIsNotProfit() {
        var rate = evaluator.calculate(List.of(transfer("2025-01-01", IN, "1000"),
                transfer("2026-01-01", IN, "500")), LocalDate.parse("2026-01-01"),
                new BigDecimal("1500"), true);
        assertEquals(0.0, rate.orElseThrow().doubleValue(), 1e-8);
    }
    @Test void missingOrPartialValuationIsNeverZeroProfit() {
        var transfers = List.of(transfer("2025-01-01", IN, "1000"));
        assertTrue(evaluator.calculate(transfers, LocalDate.parse("2026-01-01"), null, true).isEmpty());
        assertTrue(evaluator.calculate(transfers, LocalDate.parse("2026-01-01"), new BigDecimal("1100"), false).isEmpty());
    }
    @Test void futureMovementDoesNotLeakIntoHistory() {
        assertTrue(evaluator.calculate(List.of(transfer("2026-02-01", IN, "1000")),
                LocalDate.parse("2026-01-01"), new BigDecimal("1000"), true).isEmpty());
    }
    @Test void requiresActualExternalTransferAndNonnegativeEquity() {
        assertTrue(evaluator.calculate(List.of(), LocalDate.parse("2026-01-01"), BigDecimal.TEN, true).isEmpty());
        assertTrue(evaluator.calculate(List.of(transfer("2025-01-01", IN, "100")),
                LocalDate.parse("2026-01-01"), new BigDecimal("-1"), true).isEmpty());
    }
    @Test void rejectsNegativeOrZeroMovementAmounts() {
        assertThrows(IllegalArgumentException.class, () -> transfer("2025-01-01", IN, "0"));
        assertThrows(IllegalArgumentException.class, () -> transfer("2025-01-01", OUT, "-100"));
    }
}
