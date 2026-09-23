package com.investmanager.api.benchmark.calculator;

import com.investmanager.api.benchmark.entity.BenchmarkQuote;
import com.investmanager.api.benchmark.model.BenchmarkType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class IbovReturnCalculatorTest {

    private IbovReturnCalculator calculator;

    @BeforeEach
    void setUp() {
        calculator = new IbovReturnCalculator();
    }

    @Test
    void shouldReturnEmptyListWhenQuotesAreEmpty() {

        List<IbovReturnCalculator.ReturnPoint> result =
                calculator.calculate(List.of());

        assertThat(result).isEmpty();
    }

    @Test
    void shouldStartAtZeroReturn() {

        List<BenchmarkQuote> quotes = List.of(
                quote(
                        LocalDate.of(2026, 9, 1),
                        "140000.00"
                )
        );

        List<IbovReturnCalculator.ReturnPoint> result =
                calculator.calculate(quotes);

        assertThat(result).hasSize(1);

        assertThat(result.getFirst().date())
                .isEqualTo(LocalDate.of(2026, 9, 1));

        assertThat(result.getFirst().accumulatedReturn())
                .isEqualByComparingTo("0");
    }

    @Test
    void shouldCalculateAccumulatedReturnFromBaseValue() {

        List<BenchmarkQuote> quotes = List.of(
                quote(
                        LocalDate.of(2026, 9, 1),
                        "140000.00"
                ),
                quote(
                        LocalDate.of(2026, 9, 2),
                        "141400.00"
                ),
                quote(
                        LocalDate.of(2026, 9, 3),
                        "138600.00"
                )
        );

        List<IbovReturnCalculator.ReturnPoint> result =
                calculator.calculate(quotes);

        assertThat(result).hasSize(3);

        assertThat(result.get(0).accumulatedReturn())
                .isEqualByComparingTo("0");

        assertThat(result.get(1).accumulatedReturn())
                .isEqualByComparingTo("0.0100000000");

        assertThat(result.get(2).accumulatedReturn())
                .isEqualByComparingTo("-0.0100000000");
    }

    @Test
    void shouldOrderQuotesByDateBeforeCalculating() {

        List<BenchmarkQuote> quotes = List.of(
                quote(
                        LocalDate.of(2026, 9, 3),
                        "138600.00"
                ),
                quote(
                        LocalDate.of(2026, 9, 1),
                        "140000.00"
                ),
                quote(
                        LocalDate.of(2026, 9, 2),
                        "141400.00"
                )
        );

        List<IbovReturnCalculator.ReturnPoint> result =
                calculator.calculate(quotes);

        assertThat(result)
                .extracting(IbovReturnCalculator.ReturnPoint::date)
                .containsExactly(
                        LocalDate.of(2026, 9, 1),
                        LocalDate.of(2026, 9, 2),
                        LocalDate.of(2026, 9, 3)
                );

        assertThat(result.get(0).accumulatedReturn())
                .isEqualByComparingTo("0");

        assertThat(result.get(1).accumulatedReturn())
                .isEqualByComparingTo("0.0100000000");

        assertThat(result.get(2).accumulatedReturn())
                .isEqualByComparingTo("-0.0100000000");
    }

    @Test
    void shouldReturnEmptyListWhenBaseValueIsZero() {

        List<BenchmarkQuote> quotes = List.of(
                quote(
                        LocalDate.of(2026, 9, 1),
                        "0.00"
                ),
                quote(
                        LocalDate.of(2026, 9, 2),
                        "141400.00"
                )
        );

        List<IbovReturnCalculator.ReturnPoint> result =
                calculator.calculate(quotes);

        assertThat(result).isEmpty();
    }

    private BenchmarkQuote quote(
            LocalDate date,
            String value) {

        return new BenchmarkQuote(
                BenchmarkType.IBOV,
                date,
                new BigDecimal(value)
        );
    }
}