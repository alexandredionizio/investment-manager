package com.investmanager.api.valuation;

import com.investmanager.api.asset.AssetType;
import com.investmanager.api.position.dto.PositionResponse;
import java.math.*;
import java.time.Instant;

public record PositionValuationResponse(Long assetId, String assetTicker, BigDecimal quantity,
        BigDecimal averagePrice, BigDecimal totalCost, BigDecimal currentPrice, BigDecimal currentValue,
        BigDecimal profitLoss, BigDecimal profitabilityPercent, AssetType assetType, String valuationStatus,
        String valuationMethod, Instant referenceTime, String source, String reason, BigDecimal netValue) {
    public static PositionValuationResponse unavailable(PositionResponse p, AssetType type, String reason) {
        return new PositionValuationResponse(p.assetId(),p.assetTicker(),p.quantity(),p.averagePrice(),p.totalCost(),
                null,null,null,null,type,"UNAVAILABLE",null,null,null,reason,null);
    }
    public static PositionValuationResponse valued(PositionResponse p, AssetType type, BigDecimal price,
            BigDecimal value, String method, Instant reference, String source, String note, BigDecimal net) {
        BigDecimal gross=value.setScale(2,RoundingMode.HALF_UP),profit=gross.subtract(p.totalCost());
        BigDecimal percent=p.totalCost().signum()>0?profit.multiply(BigDecimal.valueOf(100)).divide(p.totalCost(),4,RoundingMode.HALF_UP):null;
        return new PositionValuationResponse(p.assetId(),p.assetTicker(),p.quantity(),p.averagePrice(),p.totalCost(),
                price,gross,profit,percent,type,method.equals("CONTRACTUAL")?"ESTIMATED":"AVAILABLE",method,reference,source,note,net);
    }
}
