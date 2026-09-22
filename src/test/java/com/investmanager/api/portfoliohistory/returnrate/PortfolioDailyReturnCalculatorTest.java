package com.investmanager.api.portfoliohistory.returnrate;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PortfolioDailyReturnCalculatorTest {

    private final PortfolioDailyReturnCalculator calculator =
            new PortfolioDailyReturnCalculator();

    @Test
    void shouldReturnZeroWhenPortfolioValueDoesNotChange() {
        PortfolioDailyReturnInput input = new PortfolioDailyReturnInput(
                LocalDate.of(2026, 9, 21),
                new BigDecimal("10000.00"),
                new BigDecimal("10000.00"),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO
        );

        BigDecimal result = calculator.calculate(input);

        assertEquals(0, result.compareTo(BigDecimal.ZERO));
    }

    @Test
    void shouldNeutralizePurchase() {
        PortfolioDailyReturnInput input = new PortfolioDailyReturnInput(
                LocalDate.of(2026, 9, 21),
                new BigDecimal("10000.00"),
                new BigDecimal("15000.00"),
                new BigDecimal("5000.00"),
                BigDecimal.ZERO,
                BigDecimal.ZERO
        );

        BigDecimal result = calculator.calculate(input);

        assertEquals(0, result.compareTo(BigDecimal.ZERO));
    }

    @Test
    void shouldNeutralizeSale() {
        PortfolioDailyReturnInput input = new PortfolioDailyReturnInput(
                LocalDate.of(2026, 9, 21),
                new BigDecimal("10000.00"),
                new BigDecimal("6000.00"),
                BigDecimal.ZERO,
                new BigDecimal("4000.00"),
                BigDecimal.ZERO
        );

        BigDecimal result = calculator.calculate(input);

        assertEquals(0, result.compareTo(BigDecimal.ZERO));
    }

    @Test
    void shouldIncludeIncomeInReturn() {
        PortfolioDailyReturnInput input = new PortfolioDailyReturnInput(
                LocalDate.of(2026, 9, 21),
                new BigDecimal("10000.00"),
                new BigDecimal("10000.00"),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                new BigDecimal("200.00")
        );

        BigDecimal result = calculator.calculate(input);

        assertEquals(
                0,
                result.compareTo(new BigDecimal("0.0200000000"))
        );
    }

    @Test
    void shouldCalculatePositiveMarketReturn() {
        PortfolioDailyReturnInput input = new PortfolioDailyReturnInput(
                LocalDate.of(2026, 9, 21),
                new BigDecimal("10000.00"),
                new BigDecimal("11000.00"),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO
        );

        BigDecimal result = calculator.calculate(input);

        assertEquals(
                0,
                result.compareTo(new BigDecimal("0.1000000000"))
        );
    }

    @Test
    void shouldCalculateNegativeMarketReturn() {
        PortfolioDailyReturnInput input = new PortfolioDailyReturnInput(
                LocalDate.of(2026, 9, 21),
                new BigDecimal("10000.00"),
                new BigDecimal("9000.00"),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO
        );

        BigDecimal result = calculator.calculate(input);

        assertEquals(
                0,
                result.compareTo(new BigDecimal("-0.1000000000"))
        );
    }

    @Test
    void shouldReturnZeroWhenDenominatorIsZero() {
        PortfolioDailyReturnInput input = new PortfolioDailyReturnInput(
                LocalDate.of(2026, 9, 21),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO
        );

        BigDecimal result = calculator.calculate(input);

        assertEquals(
                0,
                result.compareTo(BigDecimal.ZERO)
        );
    }
}