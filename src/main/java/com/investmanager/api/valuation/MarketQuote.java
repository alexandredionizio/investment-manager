package com.investmanager.api.valuation;
import java.math.BigDecimal;
import java.time.Instant;
public record MarketQuote(BigDecimal price, String currency, Instant referenceTime, String source) {
    public MarketQuote {
        if (price == null || price.signum() <= 0 || !"BRL".equals(currency) || referenceTime == null || source == null)
            throw new IllegalArgumentException("Cotação em BRL inválida");
    }
}
