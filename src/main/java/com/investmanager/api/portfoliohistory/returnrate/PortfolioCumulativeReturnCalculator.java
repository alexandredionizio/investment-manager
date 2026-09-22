package com.investmanager.api.portfoliohistory.returnrate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

public class PortfolioCumulativeReturnCalculator {

    private static final int SCALE = 10;

    public BigDecimal calculate(List<BigDecimal> dailyReturns) {

        if (dailyReturns.isEmpty()) {
            return BigDecimal.ZERO;
        }

        BigDecimal accumulatedFactor = BigDecimal.ONE;

        for (BigDecimal dailyReturn : dailyReturns) {
            accumulatedFactor = accumulatedFactor.multiply(
                    BigDecimal.ONE.add(dailyReturn)
            );
        }

        return accumulatedFactor
                .subtract(BigDecimal.ONE)
                .setScale(SCALE, RoundingMode.HALF_UP);
    }
}