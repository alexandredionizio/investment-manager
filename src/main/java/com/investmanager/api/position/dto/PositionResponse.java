package com.investmanager.api.position.dto;

import java.math.BigDecimal;

public record PositionResponse(
        Long assetId,
        String assetTicker,
        BigDecimal quantity,
        BigDecimal averagePrice,
        BigDecimal totalCost
) {
}
