package com.investmanager.api.quote.client;

import com.investmanager.api.quote.client.dto.BrapiQuoteResponse;
import com.investmanager.api.quote.exception.QuoteNotFoundException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

@Component
public class BrapiClient {

    private final RestClient restClient;

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
