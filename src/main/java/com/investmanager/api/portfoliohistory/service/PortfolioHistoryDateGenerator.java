package com.investmanager.api.portfoliohistory.service;

import com.investmanager.api.portfoliohistory.model.PortfolioHistoryGranularity;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Component
public class PortfolioHistoryDateGenerator {

    public List<LocalDate> generate(
            LocalDate startDate,
            LocalDate endDate,
            PortfolioHistoryGranularity granularity) {

        if (startDate == null || endDate == null) {
            throw new IllegalArgumentException(
                    "Datas inicial e final são obrigatórias"
            );
        }

        if (granularity == null) {
            throw new IllegalArgumentException(
                    "Granularidade é obrigatória"
            );
        }

        if (endDate.isBefore(startDate)) {
            throw new IllegalArgumentException(
                    "Data final não pode ser anterior à data inicial"
            );
        }

        List<LocalDate> dates =
                new ArrayList<>();

        LocalDate currentDate =
                startDate;

        while (!currentDate.isAfter(endDate)) {

            dates.add(currentDate);

            currentDate =
                    nextDate(
                            currentDate,
                            granularity
                    );
        }

        if (!dates.getLast().equals(endDate)) {
            dates.add(endDate);
        }

        return dates;
    }

    private LocalDate nextDate(
            LocalDate currentDate,
            PortfolioHistoryGranularity granularity) {

        return switch (granularity) {
            case DAILY -> currentDate.plusDays(1);
            case WEEKLY -> currentDate.plusWeeks(1);
            case MONTHLY -> currentDate.plusMonths(1);
        };
    }
}