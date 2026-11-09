package com.investmanager.api.operational;

import com.investmanager.api.benchmark.client.bcb.BcbClient;
import com.investmanager.api.quote.client.BrapiClient;
import com.investmanager.api.shared.config.RestClientConfig;
import com.investmanager.api.shared.integration.*;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import java.math.BigDecimal;
import java.nio.file.*;
import java.time.*;
import java.time.format.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntSupplier;
import static org.assertj.core.api.Assertions.*;

/** Opt-in only: mvnw -Dtest=RealProviderProbe test. Never discovered by the regression suite. */
class RealProviderProbe {
    @Test void readConfiguredProviders() throws Exception {
        var results = new ArrayList<Map<String, Object>>();
        var policy = new IntegrationProperties();
        var token = System.getenv("BRAPI_TOKEN");
        try (var calls = new CallsResource(policy)) {
            var bcbCount = new AtomicInteger();
            var bcb = new BcbClient(RestClientConfig.builder(policy.getBcb()).baseUrl("https://api.bcb.gov.br")
                    .requestInterceptor((request, body, execution) -> {
                        bcbCount.incrementAndGet();
                        return execution.execute(request, body);
                    }).build(), calls.value);
            var format = DateTimeFormatter.ofPattern("dd/MM/uuuu").withResolverStyle(ResolverStyle.STRICT);
            probe(results, "BCB_CDI_12", bcbCount, () -> {
                var points = bcb.getSeries(12, LocalDate.of(2026,10,29), LocalDate.of(2026,10,30));
                assertThat(points).isNotEmpty();
                points.forEach(p -> { LocalDate.parse(p.date(), format); new BigDecimal(p.value()); });
                return points.size();
            });
            probe(results, "BCB_WEEKEND", bcbCount, () -> {
                var points = bcb.getSeries(12, LocalDate.of(2026,10,31), LocalDate.of(2026,11,1));
                assertThat(points).isEmpty();
                return points.size();
            });
            if (token == null || token.isBlank()) {
                results.add(Map.of("operation", "BRAPI", "status", "NO_CONFIGURED_TOKEN"));
            } else {
                var count = new AtomicInteger();
                var brapi = new BrapiClient(RestClientConfig.builder(policy.getBrapi()).baseUrl("https://brapi.dev")
                        .defaultHeaders(h -> h.setBearerAuth(token))
                        .requestInterceptor((request, body, execution) -> {
                            assertThat(request.getURI().getScheme()).isEqualTo("https");
                            // Boolean assertion prevents credentials appearing in assertion diffs.
                            assertThat(("Bearer " + token).equals(request.getHeaders().getFirst("Authorization"))).isTrue();
                            assertThat(request.getURI().toString().contains(token)).isFalse();
                            count.incrementAndGet();
                            return execution.execute(request, body);
                        }).build(), calls.value);
                for (String symbol : List.of("PETR4", "HGLG11", "BOVA11")) {
                    probe(results, "BRAPI_QUOTE_" + symbol, count, () -> {
                        var rows = brapi.getQuote(symbol).results();
                        assertThat(rows).hasSize(1);
                        assertThat(rows.getFirst().symbol()).isEqualTo(symbol);
                        assertThat(rows.getFirst().data().regularMarketPrice()).isPositive();
                        return rows.size();
                    });
                    probe(results, "BRAPI_HISTORY_" + symbol, count, () -> {
                        var rows = brapi.getHistoricalQuotes(symbol, "5d", "1d").results();
                        assertThat(rows.getFirst().symbol()).isEqualTo(symbol);
                        var points = rows.getFirst().data().historicalDataPrice();
                        assertThat(points).isNotEmpty();
                        points.forEach(p -> Instant.ofEpochSecond(p.date()));
                        return points.size();
                    });
                }
                probe(results, "BRAPI_IBOV", count, () -> {
                    var rows = brapi.getIndexHistoricalQuotes("^BVSP", "5d", "1d").results();
                    assertThat(rows).hasSize(1);
                    assertThat(rows.getFirst().symbol()).isEqualTo("^BVSP");
                    assertThat(rows.getFirst().historicalDataPrice()).isNotEmpty();
                    return rows.getFirst().historicalDataPrice().size();
                });
            }
        }
        Files.createDirectories(Path.of("target/operational"));
        Files.writeString(Path.of("target/operational/real-providers.json"),
                new JsonMapper().writerWithDefaultPrettyPrinter().writeValueAsString(results));
    }

    private void probe(List<Map<String,Object>> results, String name, AtomicInteger count, IntSupplier action) {
        int before = count.get();
        long start = System.nanoTime();
        var row = new LinkedHashMap<String,Object>();
        row.put("operation", name);
        try { row.put("rows", action.getAsInt()); row.put("status", "OK"); }
        catch (ExternalIntegrationException e) { row.put("status", e.status()); row.put("category", e.category()); }
        catch (Exception | AssertionError e) { row.put("status", "FAILED"); row.put("category", e.getClass().getSimpleName()); }
        row.put("httpCalls", count.get() - before);
        row.put("elapsedMs", Duration.ofNanos(System.nanoTime()-start).toMillis());
        results.add(row);
        System.out.println("REAL_PROBE " + row);
    }
    private static class CallsResource implements AutoCloseable {
        final ExternalCalls value;
        CallsResource(IntegrationProperties p) { value = new ExternalCalls(p); }
        public void close() { value.close(); }
    }
}
