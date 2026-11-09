package com.investmanager.api.quote.service;

import com.investmanager.api.quote.client.BrapiClient;
import com.investmanager.api.quote.client.dto.BrapiQuoteResponse;
import com.investmanager.api.quote.client.dto.BrapiQuoteResult;
import com.investmanager.api.quote.exception.QuoteNotFoundException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import com.investmanager.api.shared.integration.MarketDataCache;
import com.investmanager.api.shared.integration.IntegrationProperties;

import java.math.BigDecimal;
import java.time.Duration;

@Service
public class QuoteService {

    private final BrapiClient brapiClient;
    private final AssetQuoteSupport quoteSupport;
    private final MarketDataCache cache;
    private final IntegrationProperties properties;

    public QuoteService(BrapiClient brapiClient, StringRedisTemplate redis, AssetQuoteSupport support) {
        this(brapiClient, new MarketDataCache(redis), support, new IntegrationProperties());
    }
    @Autowired
    public QuoteService(BrapiClient brapiClient, MarketDataCache cache, AssetQuoteSupport support, IntegrationProperties properties) {
        this.brapiClient = brapiClient;
        this.cache = cache;
        this.quoteSupport = support;
        this.properties = properties;
    }

    public BigDecimal getCurrentPrice (String symbol) {

        symbol = quoteSupport.requireSupported(symbol);
        String cacheKey = "quote:brl:v2:" + symbol;

        String cachedPrice =
                cache.get(cacheKey);

        if (cachedPrice != null) {
            try {
                BigDecimal cached = new BigDecimal(cachedPrice);
                if (cached.signum() >= 0) return cached;
            } catch (NumberFormatException ignored) { /* Corrupt entries must not become prices. */ }
            cache.invalidEntry();
        }

        BrapiQuoteResponse response =
                brapiClient.getQuote(symbol);

        if (response == null || response.results() == null) {
            throw new IllegalStateException("Resposta inválida do provedor de cotações");
        }
        if (response.results().isEmpty()) {

            throw new QuoteNotFoundException(symbol);
        }

        BrapiQuoteResult result =
                response.results().getFirst();

        if (result == null || result.data() == null || result.data().regularMarketPrice() == null) {
            throw new IllegalStateException("Resposta inválida do provedor de cotações");
        }
        if (!"BRL".equals(result.data().currency())) {
            throw new IllegalStateException("Moeda incompatível com a avaliação em reais");
        }
        BigDecimal price = result.data().regularMarketPrice();
        if (price.signum() < 0) throw new IllegalStateException("Preço inválido do provedor de cotações");

        cache.put(
                cacheKey,
                price.toPlainString(),
                Duration.ofSeconds(properties.getQuoteTtlSeconds())
        );

        return price;
    }
}
