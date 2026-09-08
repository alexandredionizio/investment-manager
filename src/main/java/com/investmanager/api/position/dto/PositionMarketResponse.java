package com.investmanager.api.position.dto;

import java.math.BigDecimal;

public record PositionMarketResponse(
        Long assetId,
        String assetTicker,
        BigDecimal quantity,
        BigDecimal averagePrice,
        BigDecimal totalCost,

        BigDecimal currentPrice,
        BigDecimal currentValue,
        BigDecimal profitLoss,
        BigDecimal profitabilityPercent
) {
}
