package com.investmanager.api.portfoliohistory.xirr;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class XirrCalculatorTest {
    private final XirrCalculator calculator = new XirrCalculator();
    private static DatedCashFlow flow(String date, String amount) {
        return new DatedCashFlow(LocalDate.parse(date), new BigDecimal(amount));
    }

    @Test void oneYearGainIsTenPercent() {
        var result = calculator.calculate(List.of(flow("2025-01-01", "-1000"), flow("2026-01-01", "1100")));
        assertEquals(0.10, result.orElseThrow().doubleValue(), 1e-8);
    }

    @Test void oneYearLossIsTenPercent() {
        var result = calculator.calculate(List.of(flow("2025-01-01", "-1000"), flow("2026-01-01", "900")));
        assertEquals(-0.10, result.orElseThrow().doubleValue(), 1e-8);
    }

    @Test void irregularCashFlowsProduceExpectedRate() {
        // On actual/365 basis, a 10% rate discounts each cash flow to a zero NPV.
        double terminal = 1000 * 1.1 - 200 * Math.pow(1.1, 1 - 180.0 / 365);
        var result = calculator.calculate(List.of(
                flow("2025-01-01", "-1000"),
                flow("2025-06-30", "200"),
                flow("2026-01-01", BigDecimal.valueOf(terminal).toPlainString())));
        assertEquals(0.10, result.orElseThrow().doubleValue(), 1e-7);
    }

    @Test void orderDoesNotMatter() {
        var result = calculator.calculate(List.of(flow("2026-01-01", "1100"), flow("2025-01-01", "-1000")));
        assertEquals(0.10, result.orElseThrow().doubleValue(), 1e-8);
    }

    @Test void emptyWhenNoCapitalOutflow() {
        assertTrue(calculator.calculate(List.of(flow("2025-01-01", "100"),flow("2026-01-01", "200"))).isEmpty());
    }

    @Test void emptyWhenSameDateOrNoFlows() {
        assertTrue(calculator.calculate(List.of(flow("2025-01-01", "-100"),flow("2025-01-01", "200"))).isEmpty());
        assertTrue(calculator.calculate(List.of()).isEmpty());
    }

    @Test void emptyWhenNoSolution() {
        assertTrue(calculator.calculate(List.of(flow("2025-01-01", "-100"),flow("2026-01-01", "0"))).isEmpty());
    }

    @Test void emptyWhenMultipleRatesArePossible() {
        // NPV = -100 + 230/(1+r) -132/(1+r)^2; r = 10% and r = 20%.
        assertTrue(calculator.calculate(List.of(flow("2025-01-01", "-100"),
                flow("2026-01-01", "230"),flow("2027-01-01", "-132"))).isEmpty());
    }
}
