package com.investmanager.api.benchmark.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record BenchmarkComparisonPointResponse(

        LocalDate date,

        BigDecimal portfolioReturn,

        BigDecimal cdiReturn,

        BigDecimal ibovReturn

) {
}