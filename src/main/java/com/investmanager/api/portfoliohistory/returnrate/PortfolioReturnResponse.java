package com.investmanager.api.portfoliohistory.returnrate;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record PortfolioReturnResponse(
        Long portfolioId,
        LocalDate startDate,
        LocalDate endDate,
        @Schema(nullable = true, description = "Null when there is insufficient data to calculate the return")
        BigDecimal totalReturn,
        List<PortfolioReturnSeriesPoint> points
) {
}