package com.investmanager.api.quote.client.dto;

import java.util.List;

public record BrapiIndexHistoricalResponse(
        List<BrapiIndexHistoricalResult> results
) {
}