package com.investmanager.api.quote.client.mapper;

import com.investmanager.api.portfoliohistory.model.PortfolioHistoryGranularity;
import org.springframework.stereotype.Component;

@Component
public class BrapiHistoricalIntervalMapper {

    public String toInterval(
            PortfolioHistoryGranularity granularity) {

        return switch (granularity) {

            case DAILY ->
                    "1d";

            case WEEKLY ->
                    "1wk";

            case MONTHLY ->
                    "1mo";
        };
    }
}