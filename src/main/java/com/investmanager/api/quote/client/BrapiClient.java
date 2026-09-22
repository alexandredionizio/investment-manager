package com.investmanager.api.quote.client;

import com.investmanager.api.quote.client.dto.BrapiHistoricalResponse;
import com.investmanager.api.quote.client.dto.BrapiQuoteResponse;
import com.investmanager.api.quote.exception.QuoteNotFoundException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.time.LocalDate;

@Component
public class BrapiClient {

    private final RestClient restClient;
    private final Object requestLock = new Object();

    public BrapiClient(
            RestClient.Builder builder,
            @Value("${brapi.token}") String token) {

        this.restClient = builder
                .baseUrl("https://brapi.dev")
                .defaultHeaders(headers ->
                        headers.setBearerAuth(token)
                )
                .build();
    }

    public BrapiQuoteResponse getQuote(String symbol) {

        synchronized (requestLock) {
            try {
                return restClient.get()
                        .uri(uriBuilder -> uriBuilder
                                .path("/api/v2/stocks/quote")
                                .queryParam("symbols", symbol)
                                .build())
                        .retrieve()
                        .body(BrapiQuoteResponse.class);

            } catch (HttpClientErrorException.NotFound exception) {
                throw new QuoteNotFoundException(symbol);
            }
        }
    }

    public BrapiHistoricalResponse getHistoricalQuotes(
            String symbol,
            String range,
            String interval) {

        synchronized (requestLock) {
            try {
                return restClient.get()
                        .uri(uriBuilder -> uriBuilder
                                .path("/api/v2/stocks/historical")
                                .queryParam("symbols", symbol)
                                .queryParam("range", range)
                                .queryParam("interval", interval)
                                .build())
                        .retrieve()
                        .body(BrapiHistoricalResponse.class);

            } catch (HttpClientErrorException.NotFound exception) {
                throw new QuoteNotFoundException(symbol);
            }
        }
    }

    public BrapiHistoricalResponse getHistoricalQuotes(
            String symbol,
            LocalDate startDate,
            LocalDate endDate,
            String interval) {

        synchronized (requestLock) {
            try {
                return restClient.get()
                        .uri(uriBuilder -> uriBuilder
                                .path("/api/v2/stocks/historical")
                                .queryParam("symbols", symbol)
                                .queryParam("startDate", startDate)
                                .queryParam("endDate", endDate)
                                .queryParam("interval", interval)
                                .build())
                        .retrieve()
                        .body(BrapiHistoricalResponse.class);

            } catch (HttpClientErrorException.NotFound exception) {
                throw new QuoteNotFoundException(symbol);
            }
        }
    }
}