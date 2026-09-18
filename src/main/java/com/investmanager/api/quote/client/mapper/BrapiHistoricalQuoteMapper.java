package com.investmanager.api.quote.client.mapper;

import com.investmanager.api.quote.client.dto.BrapiHistoricalPrice;
import com.investmanager.api.quote.model.HistoricalQuote;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

@Component
public class BrapiHistoricalQuoteMapper {

    private static final ZoneId MARKET_ZONE =
            ZoneId.of("America/Sao_Paulo");

    public HistoricalQuote toHistoricalQuote(
            BrapiHistoricalPrice historicalPrice) {

        LocalDate date =
                Instant.ofEpochSecond(historicalPrice.date())
                        .atZone(MARKET_ZONE)
                        .toLocalDate();

        return new HistoricalQuote(
                date,
                historicalPrice.close()
        );
    }
}