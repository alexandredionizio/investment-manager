package com.investmanager.api.portfoliohistory.returnrate;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PortfolioCumulativeReturnCalculatorTest {

    private final PortfolioCumulativeReturnCalculator calculator =
            new PortfolioCumulativeReturnCalculator();

    @Test
    void shouldReturnZeroWhenThereAreNoReturns() {
        BigDecimal result = calculator.calculate(List.of());

        assertEquals(0, result.compareTo(BigDecimal.ZERO));
    }

    @Test
    void shouldReturnSingleDailyReturn() {
        BigDecimal result = calculator.calculate(
                List.of(new BigDecimal("0.10"))
        );

        assertEquals(
                0,
                result.compareTo(new BigDecimal("0.1000000000"))
        );
    }

    @Test
    void shouldCompoundPositiveReturnsGeometrically() {
        BigDecimal result = calculator.calculate(
                List.of(
                        new BigDecimal("0.10"),
                        new BigDecimal("0.10")
                )
        );

        assertEquals(
                0,
                result.compareTo(new BigDecimal("0.2100000000"))
        );
    }

    @Test
    void shouldCompoundPositiveAndNegativeReturns() {
        BigDecimal result = calculator.calculate(
                List.of(
                        new BigDecimal("0.10"),
                        new BigDecimal("-0.10")
                )
        );

        assertEquals(
                0,
                result.compareTo(new BigDecimal("-0.0100000000"))
        );
    }

    @Test
    void shouldCompoundMultipleDailyReturns() {
        BigDecimal result = calculator.calculate(
                List.of(
                        new BigDecimal("0.05"),
                        new BigDecimal("0.02"),
                        new BigDecimal("-0.01")
                )
        );

        assertEquals(
                0,
                result.compareTo(new BigDecimal("0.0602900000"))
        );
    }
}