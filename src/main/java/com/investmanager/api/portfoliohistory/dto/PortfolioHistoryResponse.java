package com.investmanager.api.portfoliohistory.dto;

import com.investmanager.api.portfoliohistory.model.PortfolioHistoryGranularity;
import com.investmanager.api.portfoliohistory.model.PortfolioHistoryPeriod;
import com.investmanager.api.portfoliohistory.model.PortfolioHistoryPoint;

import java.time.LocalDate;
import java.util.List;

public record PortfolioHistoryResponse(
        Long portfolioId,
        PortfolioHistoryPeriod period,
        LocalDate startDate,
        LocalDate endDate,
        PortfolioHistoryGranularity granularity,
        List<PortfolioHistoryPoint> points
) {
}