package com.investmanager.api.asset.dto;

import com.investmanager.api.asset.AssetType;

public record AssetResponse(
        Long id,
        String ticker,
        String name,
        AssetType type,
        String sector,
        String exchange
) {
}
