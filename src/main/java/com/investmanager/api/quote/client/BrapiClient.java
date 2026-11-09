package com.investmanager.api.quote.client;

import com.investmanager.api.quote.client.dto.*;
import com.investmanager.api.quote.exception.QuoteNotFoundException;
import com.investmanager.api.shared.integration.ExternalCalls;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriBuilder;
import java.net.URI;
import java.time.LocalDate;
import java.util.function.Function;
import java.util.function.Consumer;

@Component
public class BrapiClient {
    private final RestClient restClient;
    private final ExternalCalls calls;
    public BrapiClient(@Qualifier("brapiRestClient") RestClient restClient, ExternalCalls calls) {
        this.restClient = restClient;
        this.calls = calls;
    }
    public BrapiQuoteResponse getQuote(String symbol) {
        return get("quote", symbol, uri -> uri.path("/api/v2/stocks/quote").queryParam("symbols", symbol).build(),
                BrapiQuoteResponse.class, response -> {
                    if (response == null || response.results() == null) invalid();
                    if (response.results().isEmpty()) throw new QuoteNotFoundException(symbol);
                    var result = response.results().getFirst();
                    if (result == null || result.data() == null || result.data().regularMarketPrice() == null
                            || result.data().regularMarketPrice().signum() < 0 || !"BRL".equals(result.data().currency())) invalid();
                });
    }
    public BrapiHistoricalResponse getHistoricalQuotes(String symbol, String range, String interval) {
        return get("history", symbol, uri -> uri.path("/api/v2/stocks/historical")
                .queryParam("symbols", symbol).queryParam("range", range).queryParam("interval", interval).build(),
                BrapiHistoricalResponse.class, response -> validateHistory(symbol, response));
    }
    public BrapiHistoricalResponse getHistoricalQuotes(String symbol, LocalDate startDate, LocalDate endDate, String interval) {
        return get("history", symbol, uri -> uri.path("/api/v2/stocks/historical")
                .queryParam("symbols", symbol).queryParam("startDate", startDate).queryParam("endDate", endDate)
                .queryParam("interval", interval).build(), BrapiHistoricalResponse.class, response -> validateHistory(symbol, response));
    }
    public BrapiIndexHistoricalResponse getIndexHistoricalQuotes(String symbol, String range, String interval) {
        return get("ibov", symbol, uri -> uri.path("/api/quote/{symbol}").queryParam("range", range)
                .queryParam("interval", interval).build(symbol), BrapiIndexHistoricalResponse.class, response -> {
                    if (response == null || response.results() == null) invalid();
                    for (var result : response.results()) {
                        if (result == null || result.historicalDataPrice() == null) invalid();
                        for (var point : result.historicalDataPrice())
                            if (point == null || point.date() == null || point.close() == null || point.close().signum() < 0) invalid();
                    }
                });
    }
    private void validateHistory(String symbol, BrapiHistoricalResponse response) {
        if (response == null || response.results() == null) invalid();
        if (response.results().isEmpty()) throw new QuoteNotFoundException(symbol);
        var result = response.results().getFirst();
        if (result == null || result.data() == null || result.data().historicalDataPrice() == null) invalid();
        if (result.data().historicalDataPrice().isEmpty()) throw new QuoteNotFoundException(symbol);
        for (var point : result.data().historicalDataPrice())
            if (point == null || point.date() == null || point.close() == null || point.close().signum() < 0) invalid();
    }
    private <T> T get(String operation, String symbol, Function<UriBuilder, URI> uri, Class<T> type, Consumer<T> validate) {
        return calls.get("brapi", operation, () -> {
            try {
                T response = restClient.get().uri(uri).retrieve().body(type);
                validate.accept(response);
                return response;
            } catch (HttpClientErrorException.NotFound e) { throw new QuoteNotFoundException(symbol); }
        });
    }
    private static void invalid() { throw new IllegalStateException("Resposta inválida da Brapi"); }
}
