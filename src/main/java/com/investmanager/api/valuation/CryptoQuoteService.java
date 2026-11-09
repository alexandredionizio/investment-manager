package com.investmanager.api.valuation;

import com.investmanager.api.shared.integration.*;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.time.*;

@Service
public class CryptoQuoteService {
    private final CryptoQuoteProvider provider;
    private final MarketDataCache cache;
    private final IntegrationProperties properties;
    private final Clock clock;
    public CryptoQuoteService(CryptoQuoteProvider provider, MarketDataCache cache, IntegrationProperties properties, Clock clock) {
        this.provider = provider; this.cache = cache; this.properties = properties; this.clock = clock;
    }
    public MarketQuote quote(String ticker) {
        var currency = SupportedCryptocurrency.identify(ticker).orElseThrow(() ->
                new ValuationUnavailableException("Criptomoeda sem suporte de avaliação"));
        String key = "valuation:crypto:brl:v1:" + currency;
        String cached = cache.get(key);
        if (cached != null) {
            try {
                String[] parts = cached.split("[|]");
                var quote = new MarketQuote(new BigDecimal(parts[0]), "BRL", Instant.parse(parts[1]), "CoinGecko");
                if (fresh(quote)) return quote;
            } catch (RuntimeException ignored) { /* Invalid cache entries are misses. */ }
            cache.invalidEntry();
        }
        MarketQuote quote = provider.quote(currency);
        if (quote == null || !fresh(quote)) throw new ValuationUnavailableException("Cotação de criptomoeda desatualizada");
        cache.put(key, quote.price().toPlainString() + "|" + quote.referenceTime(), Duration.ofSeconds(properties.getQuoteTtlSeconds()));
        return quote;
    }
    private boolean fresh(MarketQuote quote) {
        Instant now = clock.instant();
        return !quote.referenceTime().isAfter(now.plusSeconds(60)) && !quote.referenceTime().isBefore(now.minusSeconds(900));
    }
}
