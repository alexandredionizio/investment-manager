package com.investmanager.api.portfoliohistory.returnrate;

import java.math.BigDecimal;
import java.time.LocalDate;

public record PortfolioReturnSeriesPoint(
        LocalDate date,
        BigDecimal dailyReturn,
        BigDecimal cumulativeReturn
) {
}