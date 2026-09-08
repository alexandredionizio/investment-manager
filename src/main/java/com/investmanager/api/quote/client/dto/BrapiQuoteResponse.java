package com.investmanager.api.quote.client.dto;

import java.util.List;

public record BrapiQuoteResponse(

        List<BrapiQuoteResult> results
) {
}
