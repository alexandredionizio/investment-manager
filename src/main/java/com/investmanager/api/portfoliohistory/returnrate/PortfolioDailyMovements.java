package com.investmanager.api.portfoliohistory.returnrate;

import java.math.BigDecimal;
import java.time.LocalDate;

public record PortfolioDailyMovements(
        LocalDate date,
        BigDecimal purchases,
        BigDecimal sales,
        BigDecimal incomes
) {
}