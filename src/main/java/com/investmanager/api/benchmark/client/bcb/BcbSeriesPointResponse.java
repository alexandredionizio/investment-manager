package com.investmanager.api.benchmark.client.bcb;

import com.fasterxml.jackson.annotation.JsonProperty;

public record BcbSeriesPointResponse(

        @JsonProperty("data")
        String date,

        @JsonProperty("valor")
        String value
) {
}