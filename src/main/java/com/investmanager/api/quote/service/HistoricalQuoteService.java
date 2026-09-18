package com.investmanager.api.quote.service;

import com.investmanager.api.portfoliohistory.model.PortfolioHistoryGranularity;
import com.investmanager.api.portfoliohistory.model.PortfolioHistoryPeriod;
import com.investmanager.api.quote.client.BrapiClient;
import com.investmanager.api.quote.client.dto.BrapiHistoricalResponse;
import com.investmanager.api.quote.client.mapper.BrapiHistoricalQuoteMapper;
import com.investmanager.api.quote.client.mapper.BrapiHistoricalRangeMapper;
import com.investmanager.api.quote.exception.QuoteNotFoundException;
import com.investmanager.api.quote.model.HistoricalQuote;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Service
public class HistoricalQuoteService {

    private static final String DAILY_INTERVAL = "1d";

    private final BrapiClient brapiClient;
    private final BrapiHistoricalQuoteMapper historicalQuoteMapper;
    private final BrapiHistoricalRangeMapper historicalRangeMapper;

    public HistoricalQuoteService(
            BrapiClient brapiClient,
            BrapiHistoricalQuoteMapper historicalQuoteMapper,
            BrapiHistoricalRangeMapper historicalRangeMapper) {

        this.brapiClient = brapiClient;
        this.historicalQuoteMapper = historicalQuoteMapper;
        this.historicalRangeMapper = historicalRangeMapper;
    }

    public List<HistoricalQuote> getHistoricalQuotes(
            String symbol,
            PortfolioHistoryPeriod period,
            PortfolioHistoryGranularity granularity) {

        if (period == PortfolioHistoryPeriod.CUSTOM) {
            throw new IllegalArgumentException(
                    "Período CUSTOM requer data inicial e data final"
            );
        }

        String range =
                historicalRangeMapper.toRange(period);

        BrapiHistoricalResponse response =
                brapiClient.getHistoricalQuotes(
                        symbol,
                        range,
                        DAILY_INTERVAL
                );

        return mapHistoricalQuotes(
                symbol,
                response
        );
    }

    public List<HistoricalQuote> getHistoricalQuotes(
            String symbol,
            LocalDate startDate,
            LocalDate endDate,
            PortfolioHistoryGranularity granularity) {

        if (startDate == null || endDate == null) {
            throw new IllegalArgumentException(
                    "Datas inicial e final são obrigatórias"
            );
        }

        if (endDate.isBefore(startDate)) {
            throw new IllegalArgumentException(
                    "Data final não pode ser anterior à data inicial"
            );
        }

        BrapiHistoricalResponse response =
                brapiClient.getHistoricalQuotes(
                        symbol,
                        startDate,
                        endDate,
                        DAILY_INTERVAL
                );

        return mapHistoricalQuotes(
                symbol,
                response
        );
    }

    private List<HistoricalQuote> mapHistoricalQuotes(
            String symbol,
            BrapiHistoricalResponse response) {

        if (response == null
                || response.results() == null
                || response.results().isEmpty()) {

            throw new QuoteNotFoundException(symbol);
        }

        var result =
                response.results().getFirst();

        if (result.data() == null
                || result.data().historicalDataPrice() == null
                || result.data().historicalDataPrice().isEmpty()) {

            throw new QuoteNotFoundException(symbol);
        }

        return result.data()
                .historicalDataPrice()
                .stream()
                .map(
                        historicalQuoteMapper
                                ::toHistoricalQuote
                )
                .toList();
    }
}