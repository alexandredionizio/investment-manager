package com.investmanager.api.benchmark.calculator;

import com.investmanager.api.benchmark.entity.BenchmarkQuote;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Component
public class CdiReturnCalculator {

    private static final BigDecimal ONE_HUNDRED =
            new BigDecimal("100");

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

        List<ReturnPoint> result =
                new ArrayList<>();

        BigDecimal accumulatedFactor =
                BigDecimal.ONE;

        LocalDate firstDate =
                orderedQuotes.getFirst().getDate();

        result.add(
                new ReturnPoint(
                        firstDate,
                        BigDecimal.ZERO
                )
        );

        for (int i = 1; i < orderedQuotes.size(); i++) {

            BenchmarkQuote quote =
                    orderedQuotes.get(i);

            BigDecimal dailyRate =
                    quote.getValue()
                            .divide(
                                    ONE_HUNDRED,
                                    MATH_CONTEXT
                            );

            accumulatedFactor =
                    accumulatedFactor.multiply(
                            BigDecimal.ONE.add(dailyRate),
                            MATH_CONTEXT
                    );

            BigDecimal accumulatedReturn =
                    accumulatedFactor
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
            LocalDate date,
            BigDecimal accumulatedReturn
    ) {
    }
}