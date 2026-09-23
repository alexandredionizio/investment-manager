package com.investmanager.api.benchmark.calculator;

import com.investmanager.api.benchmark.entity.BenchmarkQuote;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Component
public class IbovReturnCalculator {

    private static final MathContext MATH_CONTEXT =
            new MathContext(16, RoundingMode.HALF_UP);

    public List<ReturnPoint> calculate(
            List<BenchmarkQuote> quotes) {

        if (quotes == null || quotes.isEmpty()) {
            return List.of();
        }

        List<BenchmarkQuote> orderedQuotes =
                quotes.stream()
                        .sorted(Comparator.comparing(
                                BenchmarkQuote::getDate
                        ))
                        .toList();

        BigDecimal baseValue =
                orderedQuotes.getFirst().getValue();

        if (baseValue == null
                || baseValue.compareTo(BigDecimal.ZERO) == 0) {
            return List.of();
        }

        List<ReturnPoint> result =
                new ArrayList<>();

        for (BenchmarkQuote quote : orderedQuotes) {

            if (quote.getValue() == null) {
                continue;
            }

            BigDecimal accumulatedReturn =
                    quote.getValue()
                            .divide(
                                    baseValue,
                                    MATH_CONTEXT
                            )
                            .subtract(BigDecimal.ONE)
                            .setScale(
                                    10,
                                    RoundingMode.HALF_UP
                            );

            result.add(
                    new ReturnPoint(
                            quote.getDate(),
                            accumulatedReturn
                    )
            );
        }

        return result;
    }

    public record ReturnPoint(
            java.time.LocalDate date,
            BigDecimal accumulatedReturn
    ) {
    }
}