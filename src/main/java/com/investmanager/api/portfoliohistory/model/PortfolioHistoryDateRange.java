package com.investmanager.api.portfoliohistory.model;

import java.time.LocalDate;

public record PortfolioHistoryDateRange(
        LocalDate startDate,
        LocalDate endDate
) {
}