package com.investmanager.api.portfoliohistory.xirr;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Never expose this preliminary figure as the definitive dashboard return. */
public record PortfolioXirrPreviewResponse(
        String status, LocalDate asOf, BigDecimal annualRate, BigDecimal terminalEquity,
        BigDecimal cashBalance, BigDecimal positionsValue, int externalMovements,
        String notice) {
}
