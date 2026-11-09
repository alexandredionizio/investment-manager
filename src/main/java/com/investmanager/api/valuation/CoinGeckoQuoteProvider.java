package com.investmanager.api.valuation;

import com.investmanager.api.shared.integration.ExternalCalls;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.Map;

@Component
public class CoinGeckoQuoteProvider implements CryptoQuoteProvider {
    private static final Map<SupportedCryptocurrency, String> IDS = Map.of(
            SupportedCryptocurrency.BTC, "bitcoin", SupportedCryptocurrency.ETH, "ethereum");
    private final RestClient client;
    private final ExternalCalls calls;
    public CoinGeckoQuoteProvider(@Qualifier("cryptoRestClient") RestClient client, ExternalCalls calls) {
        this.client = client; this.calls = calls;
    }
    public MarketQuote quote(SupportedCryptocurrency currency) {
        return calls.get("coingecko", "price-brl", () -> {
            String id = IDS.get(currency);
            JsonNode body = client.get().uri(uri -> uri.path("/simple/price")
                    .queryParam("ids", id).queryParam("vs_currencies", "brl")
                    .queryParam("include_last_updated_at", true).queryParam("precision", "full").build())
                    .retrieve().body(JsonNode.class);
            JsonNode value = body == null ? null : body.get(id);
            if (value == null || !value.path("brl").isNumber() || !value.path("last_updated_at").isIntegralNumber())
                throw new IllegalStateException("Resposta inválida da CoinGecko");
            return new MarketQuote(value.get("brl").decimalValue(), "BRL",
                    Instant.ofEpochSecond(value.get("last_updated_at").asLong()), "CoinGecko");
        });
    }
}
