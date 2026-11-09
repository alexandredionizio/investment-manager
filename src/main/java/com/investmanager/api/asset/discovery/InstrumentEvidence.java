package com.investmanager.api.asset.discovery;
import com.investmanager.api.asset.AssetType;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
/** Evidence from a trusted catalog/adapter, never accepted directly from REST input. */
public record InstrumentEvidence(String ticker, String name, AssetType type, String exchange,
        String isin, List<Alias> aliases, String source, boolean active) {
    public record Alias(String ticker, LocalDate effectiveDate) {}
    public InstrumentEvidence {
        ticker = normalize(ticker); exchange = normalize(exchange); isin = isin == null ? null : normalize(isin);
        aliases = aliases == null ? List.of() : List.copyOf(aliases);
        if (ticker == null || !ticker.matches("[A-Z0-9][A-Z0-9._-]{1,19}") || name == null || name.isBlank()
                || name.length() > 120 || type == null || exchange == null || exchange.isBlank() || exchange.length() > 50
                || source == null || source.isBlank() || source.length() > 50
                || (isin != null && !isin.matches("[A-Z]{2}[A-Z0-9]{9}[0-9]"))) throw InstrumentException.ineligible();
        for (Alias alias : aliases) if (alias.ticker() == null || !normalize(alias.ticker()).matches("[A-Z0-9][A-Z0-9._-]{1,19}")
                || alias.effectiveDate() == null) throw InstrumentException.ambiguous();
    }
    public static String normalize(String value) { return value == null ? null : value.strip().toUpperCase(Locale.ROOT); }
}
