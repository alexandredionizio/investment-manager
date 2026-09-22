package com.investmanager.api.portfoliohistory.returnrate;

import java.math.BigDecimal;
import java.math.RoundingMode;

public class PortfolioDailyReturnCalculator {

    private static final int SCALE = 10;

    public BigDecimal calculate(PortfolioDailyReturnInput input) {

        BigDecimal denominator = input.previousMarketValue()
                .add(input.purchases());

        if (denominator.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }

        BigDecimal numerator = input.currentMarketValue()
                .add(input.sales())
                .add(input.incomes());

        return numerator
                .divide(denominator, SCALE, RoundingMode.HALF_UP)
                .subtract(BigDecimal.ONE);
    }
}