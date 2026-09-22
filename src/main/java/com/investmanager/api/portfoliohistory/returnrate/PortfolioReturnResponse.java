package com.investmanager.api.portfoliohistory.returnrate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record PortfolioReturnResponse(
        Long portfolioId,
        LocalDate startDate,
        LocalDate endDate,
        BigDecimal totalReturn,
        List<PortfolioReturnSeriesPoint> points
) {
}