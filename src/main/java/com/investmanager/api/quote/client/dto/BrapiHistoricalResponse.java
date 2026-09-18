package com.investmanager.api.quote.client.dto;

import java.util.List;

public record BrapiHistoricalResponse(
        List<BrapiHistoricalResult> results,
        String requestedAt,
        Long took
) {
}