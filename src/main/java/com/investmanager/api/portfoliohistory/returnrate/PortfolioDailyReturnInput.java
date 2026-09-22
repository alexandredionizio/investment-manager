package com.investmanager.api.portfoliohistory.returnrate;

import java.math.BigDecimal;
import java.time.LocalDate;

public record PortfolioDailyReturnInput(
        LocalDate date,
        BigDecimal previousMarketValue,
        BigDecimal currentMarketValue,
        BigDecimal purchases,
        BigDecimal sales,
        BigDecimal incomes
) {
}