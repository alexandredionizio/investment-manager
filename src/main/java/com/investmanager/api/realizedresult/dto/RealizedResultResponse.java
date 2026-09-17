package com.investmanager.api.realizedresult.dto;

import java.math.BigDecimal;

public record RealizedResultResponse(

        Long assetId,
        String assetTicker,
        BigDecimal soldQuantity,
        BigDecimal totalSaleValue,
        BigDecimal totalSoldCost,
        BigDecimal realizedProfitLoss

) {
}