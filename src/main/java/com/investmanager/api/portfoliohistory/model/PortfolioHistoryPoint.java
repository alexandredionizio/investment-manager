package com.investmanager.api.portfoliohistory.model;

import java.math.BigDecimal;
import java.time.LocalDate;

public record PortfolioHistoryPoint(
        LocalDate date,
        BigDecimal value
) {
}