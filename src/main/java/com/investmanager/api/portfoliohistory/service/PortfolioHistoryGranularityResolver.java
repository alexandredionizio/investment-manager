package com.investmanager.api.portfoliohistory.service;

import com.investmanager.api.portfoliohistory.model.PortfolioHistoryGranularity;
import com.investmanager.api.portfoliohistory.model.PortfolioHistoryPeriod;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Component
public class PortfolioHistoryGranularityResolver {

    public PortfolioHistoryGranularity resolve(
            PortfolioHistoryPeriod period,
            LocalDate startDate,
            LocalDate endDate) {

        return switch (period) {

            case ONE_MONTH,
                 THREE_MONTHS,
                 SIX_MONTHS ->
                    PortfolioHistoryGranularity.DAILY;

            case ONE_YEAR,
                 TWO_YEARS ->
                    PortfolioHistoryGranularity.WEEKLY;

            case FIVE_YEARS,
                 TEN_YEARS,
                 ALL ->
                    PortfolioHistoryGranularity.MONTHLY;

            case CUSTOM ->
                    resolveCustom(
                            startDate,
                            endDate
                    );
        };
    }

    private PortfolioHistoryGranularity resolveCustom(
            LocalDate startDate,
            LocalDate endDate) {

        if (startDate == null || endDate == null) {
            throw new IllegalArgumentException(
                    "Datas inicial e final são obrigatórias para período CUSTOM"
            );
        }

        if (endDate.isBefore(startDate)) {
            throw new IllegalArgumentException(
                    "Data final não pode ser anterior à data inicial"
            );
        }

        LocalDate sixMonthsAfterStart =
                startDate.plusMonths(6);

        LocalDate twoYearsAfterStart =
                startDate.plusYears(2);

        if (!endDate.isAfter(sixMonthsAfterStart)) {
            return PortfolioHistoryGranularity.DAILY;
        }

        if (!endDate.isAfter(twoYearsAfterStart)) {
            return PortfolioHistoryGranularity.WEEKLY;
        }

        return PortfolioHistoryGranularity.MONTHLY;
    }
}