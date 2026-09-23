package com.investmanager.api.benchmark.calculator;

import com.investmanager.api.benchmark.entity.BenchmarkQuote;
import com.investmanager.api.benchmark.model.BenchmarkType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CdiReturnCalculatorTest {

    private CdiReturnCalculator calculator;

    @BeforeEach
    void setUp() {
        calculator = new CdiReturnCalculator();
    }

    @Test
    void shouldReturnEmptyListWhenQuotesAreEmpty() {

        List<CdiReturnCalculator.ReturnPoint> result =
                calculator.calculate(List.of());

        assertThat(result).isEmpty();
    }

    @Test
    void shouldStartAtZeroReturn() {

        List<BenchmarkQuote> quotes = List.of(
                quote(
                        LocalDate.of(2026, 9, 1),
                        "0.05500000"
                )
        );

        List<CdiReturnCalculator.ReturnPoint> result =
                calculator.calculate(quotes);

        assertThat(result).hasSize(1);

        assertThat(result.getFirst().date())
                .isEqualTo(LocalDate.of(2026, 9, 1));

        assertThat(result.getFirst().accumulatedReturn())
                .isEqualByComparingTo("0");
    }

    @Test
    void shouldCompoundDailyCdiRates() {

        List<BenchmarkQuote> quotes = List.of(
                quote(
                        LocalDate.of(2026, 9, 1),
                        "0.05500000"
                ),
                quote(
                        LocalDate.of(2026, 9, 2),
                        "0.05500000"
                ),
                quote(
                        LocalDate.of(2026, 9, 3),
                        "0.05500000"
                )
        );

        List<CdiReturnCalculator.ReturnPoint> result =
                calculator.calculate(quotes);

        assertThat(result).hasSize(3);

        assertThat(result.get(0).accumulatedReturn())
                .isEqualByComparingTo("0");

        assertThat(result.get(1).accumulatedReturn())
                .isEqualByComparingTo("0.0005500000");

        assertThat(result.get(2).accumulatedReturn())
                .isEqualByComparingTo("0.0011003025");
    }

    @Test
    void shouldOrderQuotesByDateBeforeCalculating() {

        List<BenchmarkQuote> quotes = List.of(
                quote(
                        LocalDate.of(2026, 9, 3),
                        "0.05500000"
                ),
                quote(
                        LocalDate.of(2026, 9, 1),
                        "0.05500000"
                ),
                quote(
                        LocalDate.of(2026, 9, 2),
                        "0.05500000"
                )
        );

        List<CdiReturnCalculator.ReturnPoint> result =
                calculator.calculate(quotes);

        assertThat(result)
                .extracting(CdiReturnCalculator.ReturnPoint::date)
                .containsExactly(
                        LocalDate.of(2026, 9, 1),
                        LocalDate.of(2026, 9, 2),
                        LocalDate.of(2026, 9, 3)
                );

        assertThat(result.get(2).accumulatedReturn())
                .isEqualByComparingTo("0.0011003025");
    }

    private BenchmarkQuote quote(
            LocalDate date,
            String value) {

        return new BenchmarkQuote(
                BenchmarkType.CDI,
                date,
                new BigDecimal(value)
        );
    }
}