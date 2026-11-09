package com.investmanager.api.asset.discovery;

import com.investmanager.api.asset.AssetType;
import com.investmanager.api.shared.integration.*;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import java.time.*;
import java.util.*;

/** Brapi /api/v2/tickers: B3/BRL stocks, units, FIIs and ETFs only. No prices are used.
 * The catalog currently supplies no ISIN. Renames with conversion require manual review.
 * Contract: https://brapi.dev/swagger/latest.json */
@Component
public class BrapiCatalogClient {
    public record Page(List<InstrumentEvidence> items, int page, boolean hasNextPage) {}
    private final RestClient http;
    private final ExternalCalls calls;
    private final MarketDataCache cache;
    private final ObjectMapper json;
    public BrapiCatalogClient(@Qualifier("brapiRestClient") RestClient http, ExternalCalls calls,
                              MarketDataCache cache, ObjectMapper json) {
        this.http = http; this.calls = calls; this.cache = cache; this.json = json;
    }
    public Page search(String query, int page, int limit) {
        String normalized = query == null ? "" : query.strip();
        if (normalized.length() < 2 || normalized.length() > 80 || normalized.chars().anyMatch(Character::isISOControl)
                || page < 1 || page > 1000 || limit < 1 || limit > 20) throw InstrumentException.invalid();
        String key = "instruments:v1:" + Base64.getUrlEncoder().withoutPadding().encodeToString(
                normalized.toUpperCase(Locale.ROOT).getBytes(java.nio.charset.StandardCharsets.UTF_8)) + ":" + page + ":" + limit;
        String cached = cache.get(key);
        if (cached != null) try { return parse(json.readTree(cached), page, limit); }
            catch (RuntimeException invalid) { cache.invalidEntry(); }
        JsonNode response = request(normalized, page, limit);
        Page result = parse(response, page, limit);
        cache.put(key, response.toString(), Duration.ofSeconds(60));
        return result;
    }
    public InstrumentEvidence confirm(String ticker) {
        String symbol = InstrumentEvidence.normalize(ticker);
        if (symbol == null || !symbol.matches("[A-Z]{4}[0-9]{1,2}")) throw InstrumentException.ineligible();
        // Revalidate eligibility on confirmation; autocomplete cache is never authorization to buy.
        Page page = parse(request(symbol, 1, 20), 1, 20);
        var matches = page.items().stream().filter(i -> i.ticker().equals(symbol)).toList();
        if (matches.size() > 1) throw InstrumentException.ambiguous();
        if (matches.isEmpty()) throw InstrumentException.ineligible();
        InstrumentEvidence item = matches.getFirst();
        JsonNode renames = call("catalog_renames", () -> http.get().uri(b -> b.path("/api/v2/tickers/renames")
                .queryParam("symbols", symbol).build()).retrieve().body(JsonNode.class));
        if (renames == null || !renames.path("results").isArray() || renames.path("results").size() > 200) throw unavailable(502);
        List<InstrumentEvidence.Alias> aliases = new ArrayList<>();
        for (JsonNode rename : renames.path("results")) {
            if (!symbol.equals(rename.path("canonicalSymbol").asText())) throw InstrumentException.ambiguous();
            if (rename.hasNonNull("conversionRatio")) throw InstrumentException.ambiguous();
            try {
                LocalDate effective = LocalDate.parse(rename.path("effectiveDate").asText());
                if (effective.isAfter(LocalDate.now(ZoneOffset.UTC))) throw InstrumentException.ambiguous();
                aliases.add(new InstrumentEvidence.Alias(rename.path("oldSymbol").asText(), effective));
                String intermediate = rename.path("newSymbol").asText();
                if (!intermediate.equals(symbol)) aliases.add(new InstrumentEvidence.Alias(intermediate, effective));
            } catch (java.time.format.DateTimeParseException invalid) { throw unavailable(502); }
        }
        return new InstrumentEvidence(item.ticker(), item.name(), item.type(), item.exchange(), null, aliases, "BRAPI", true);
    }
    private JsonNode request(String query, int page, int limit) {
        return call("catalog_search", () -> http.get().uri(b -> b.path("/api/v2/tickers")
                .queryParam("search", query).queryParam("page", page).queryParam("limit", limit)
                .queryParam("sortBy", "symbol").queryParam("sortOrder", "asc").build()).retrieve().body(JsonNode.class));
    }
    private JsonNode call(String operation, java.util.function.Supplier<JsonNode> request) {
        try { return calls.get("brapi", operation, request); }
        catch (ExternalIntegrationException failure) { throw unavailable(failure.status()); }
    }
    private InstrumentException unavailable(int status) { return new InstrumentException(status, "INSTRUMENT_CATALOG_UNAVAILABLE",
            "Catálogo de instrumentos indisponível ou com resposta inválida. Tente novamente; nenhum ativo foi cadastrado."); }
    private Page parse(JsonNode response, int page, int limit) {
        if (response == null || !response.path("results").isArray() || response.path("results").size() > limit
                || !response.path("pagination").path("page").isIntegralNumber()
                || response.path("pagination").path("page").asInt() != page
                || !response.path("pagination").path("hasNextPage").isBoolean()) throw unavailable(502);
        List<InstrumentEvidence> items = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (JsonNode row : response.path("results")) {
            if (!row.path("symbol").isTextual() || !row.path("name").isTextual()
                    || !row.path("isActive").isBoolean() || !"B3".equals(row.path("exchange").asText())
                    || !"BRL".equals(row.path("currency").asText())) throw unavailable(502);
            if (!row.path("isActive").asBoolean()) continue;
            String subtype = row.path("subType").asText("");
            String family = row.path("assetType").asText("");
            AssetType type = switch (subtype) {
                case "stock", "unit" -> "stock".equals(family) ? AssetType.STOCK : null;
                case "fii" -> "fund".equals(family) ? AssetType.FII : null;
                case "etf" -> "fund".equals(family) ? AssetType.ETF : null;
                default -> null;
            };
            if (type == null) continue; // BDR, other funds and unknown classification have no supported flow.
            String symbol = InstrumentEvidence.normalize(row.path("symbol").asText());

            if (symbol == null || !symbol.matches("[A-Z]{4}[0-9]{1,2}")) {
                continue;
            }

            if (!seen.add(symbol)) {
                throw unavailable(502);
            }
            try { items.add(new InstrumentEvidence(symbol, row.path("name").asText(), type, "B3", null, List.of(), "BRAPI", true)); }
            catch (InstrumentException invalid) { throw unavailable(502); }
        }
        return new Page(List.copyOf(items), page, response.path("pagination").path("hasNextPage").asBoolean());
    }
}
