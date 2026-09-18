package com.investmanager.api.portfoliohistory.service;

import com.investmanager.api.portfoliohistory.model.PortfolioHistoryDateRange;
import com.investmanager.api.portfoliohistory.model.PortfolioHistoryPeriod;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Component
public class PortfolioHistoryDateRangeResolver {

    public PortfolioHistoryDateRange resolve(
            PortfolioHistoryPeriod period,
            LocalDate endDate,
            LocalDate customStartDate,
            LocalDate customEndDate,
            LocalDate firstTransactionDate) {

        if (period == null) {
            throw new IllegalArgumentException(
                    "Período é obrigatório"
            );
        }

        if (period == PortfolioHistoryPeriod.CUSTOM) {
            return resolveCustom(
                    customStartDate,
                    customEndDate
            );
        }

        if (endDate == null) {
            throw new IllegalArgumentException(
                    "Data final é obrigatória"
            );
        }

        LocalDate startDate = switch (period) {
            case ONE_MONTH -> endDate.minusMonths(1);
            case THREE_MONTHS -> endDate.minusMonths(3);
            case SIX_MONTHS -> endDate.minusMonths(6);
            case ONE_YEAR -> endDate.minusYears(1);
            case TWO_YEARS -> endDate.minusYears(2);
            case FIVE_YEARS -> endDate.minusYears(5);
            case TEN_YEARS -> endDate.minusYears(10);
            case ALL -> resolveAll(firstTransactionDate);
            case CUSTOM -> throw new IllegalStateException(
                    "CUSTOM já deveria ter sido tratado"
            );
        };

        return new PortfolioHistoryDateRange(
                startDate,
                endDate
        );
    }

    private PortfolioHistoryDateRange resolveCustom(
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

        return new PortfolioHistoryDateRange(
                startDate,
                endDate
        );
    }

    private LocalDate resolveAll(
            LocalDate firstTransactionDate) {

        if (firstTransactionDate == null) {
            throw new IllegalArgumentException(
                    "Não é possível determinar o início do histórico sem transações"
            );
        }

        return firstTransactionDate;
    }
}