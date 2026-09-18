package com.investmanager.api.quote.client.mapper;

import com.investmanager.api.portfoliohistory.model.PortfolioHistoryPeriod;
import org.springframework.stereotype.Component;

@Component
public class BrapiHistoricalRangeMapper {

    public String toRange(
            PortfolioHistoryPeriod period) {

        return switch (period) {

            case ONE_MONTH ->
                    "1mo";

            case THREE_MONTHS ->
                    "3mo";

            case SIX_MONTHS ->
                    "6mo";

            case ONE_YEAR ->
                    "1y";

            case TWO_YEARS ->
                    "2y";

            case FIVE_YEARS ->
                    "5y";

            case TEN_YEARS ->
                    "10y";

            case ALL ->
                    "max";

            case CUSTOM ->
                    throw new IllegalArgumentException(
                            "Período CUSTOM não possui range predefinido na brapi"
                    );
        };
    }
}