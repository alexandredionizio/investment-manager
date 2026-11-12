package com.investmanager.api.portfoliohistory.xirr;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Converts independently recorded EXTERNAL capital transfers into investor-perspective
 * cash flows and adds a terminal portfolio equity value. It never derives external
 * movements from trades or proventos, which can be entirely internal.
 *
 * Terminal equity must include positions AND cash, net of liabilities, valued on asOf;
 * a position-only valuation is not sufficient. Missing or partial valuation => empty.
 */
public final class PortfolioXirrEvaluator {
    private final XirrCalculator calculator;

    public PortfolioXirrEvaluator() { this(new XirrCalculator()); }

    public PortfolioXirrEvaluator(XirrCalculator calculator) {
        this.calculator = Objects.requireNonNull(calculator, "calculator");
    }

    public Optional<BigDecimal> calculate(List<ExternalCapitalMovement> movements,
                                          LocalDate asOf,
                                          BigDecimal totalEquityIncludingCash,
                                          boolean completeValuation) {
        if (!completeValuation || movements == null || movements.isEmpty() || asOf == null
                || totalEquityIncludingCash == null || totalEquityIncludingCash.signum() < 0)
            return Optional.empty();
        List<DatedCashFlow> flows = new ArrayList<>(movements.size() + 1);
        for (ExternalCapitalMovement movement : movements) {
            if (movement == null || movement.date().isAfter(asOf)) return Optional.empty();
            BigDecimal signed = movement.kind() == ExternalCapitalMovement.Kind.CONTRIBUTION
                    ? movement.amount().negate() : movement.amount();
            flows.add(new DatedCashFlow(movement.date(), signed));
        }
        flows.add(new DatedCashFlow(asOf, totalEquityIncludingCash));
        return calculator.calculate(flows);
    }
}
