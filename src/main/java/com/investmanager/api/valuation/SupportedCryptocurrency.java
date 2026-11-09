package com.investmanager.api.valuation;
import java.util.Locale;
import java.util.Optional;
public enum SupportedCryptocurrency {
    BTC, ETH;
    public static Optional<SupportedCryptocurrency> identify(String ticker) {
        if (ticker == null) return Optional.empty();
        try { return Optional.of(valueOf(ticker.strip().toUpperCase(Locale.ROOT))); }
        catch (IllegalArgumentException e) { return Optional.empty(); }
    }
}
