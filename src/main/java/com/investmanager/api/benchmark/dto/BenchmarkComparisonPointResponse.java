package com.investmanager.api.benchmark.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDate;

public record BenchmarkComparisonPointResponse(

        LocalDate date,

        BigDecimal portfolioReturn,

        @Schema(nullable = true, description = "Null when there is insufficient data to calculate the return")
        BigDecimal cdiReturn,

        @Schema(nullable = true, description = "Null when there is insufficient data to calculate the return")
        BigDecimal ibovReturn

) {
}