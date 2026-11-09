package com.investmanager.api.quote.service;

import com.investmanager.api.portfoliohistory.model.PortfolioHistoryGranularity;
import com.investmanager.api.portfoliohistory.model.PortfolioHistoryPeriod;
import com.investmanager.api.quote.client.BrapiClient;
import com.investmanager.api.quote.client.dto.BrapiHistoricalResponse;
import com.investmanager.api.quote.client.mapper.BrapiHistoricalQuoteMapper;
import com.investmanager.api.quote.client.mapper.BrapiHistoricalRangeMapper;
import com.investmanager.api.quote.exception.QuoteNotFoundException;
import com.investmanager.api.quote.model.HistoricalQuote;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import com.investmanager.api.shared.integration.MarketDataCache;
import com.investmanager.api.shared.integration.IntegrationProperties;
import com.investmanager.api.shared.integration.IntegrationBudgetFilter;
import com.investmanager.api.shared.integration.ExternalIntegrationException;
import tools.jackson.databind.json.JsonMapper;
import java.time.Duration;
import java.util.Arrays;
import java.util.function.Supplier;
import java.util.concurrent.*;

import java.time.LocalDate;
import java.util.List;

@Service
public class HistoricalQuoteService {

    private static final String DAILY_INTERVAL = "1d";

    private final BrapiClient brapiClient;
    private final AssetQuoteSupport quoteSupport;
    private final BrapiHistoricalQuoteMapper historicalQuoteMapper;
    private final BrapiHistoricalRangeMapper historicalRangeMapper;

    private final MarketDataCache cache;
    private final IntegrationProperties properties;
    private final JsonMapper json = JsonMapper.builder().build();
    private final ConcurrentMap<String, CompletableFuture<List<HistoricalQuote>>> inFlight = new ConcurrentHashMap<>();

    public HistoricalQuoteService(
            BrapiClient brapiClient,
            BrapiHistoricalQuoteMapper historicalQuoteMapper,
            BrapiHistoricalRangeMapper historicalRangeMapper,
            AssetQuoteSupport quoteSupport) {

        this(brapiClient, historicalQuoteMapper, historicalRangeMapper, quoteSupport, null, new IntegrationProperties());
    }

    @Autowired
    public HistoricalQuoteService(BrapiClient brapiClient, BrapiHistoricalQuoteMapper historicalQuoteMapper,
            BrapiHistoricalRangeMapper historicalRangeMapper, AssetQuoteSupport quoteSupport,
            MarketDataCache cache, IntegrationProperties properties) {
        this.cache = cache;
        this.properties = properties;
        this.brapiClient = brapiClient;
        this.quoteSupport = quoteSupport;
        this.historicalQuoteMapper = historicalQuoteMapper;
        this.historicalRangeMapper = historicalRangeMapper;
    }

    public List<HistoricalQuote> getHistoricalQuotes(
            String symbol,
            PortfolioHistoryPeriod period,
            PortfolioHistoryGranularity granularity) {

        if (period == PortfolioHistoryPeriod.CUSTOM) {
            throw new IllegalArgumentException(
                    "Período CUSTOM requer data inicial e data final"
            );
        }

        String range =
                historicalRangeMapper.toRange(period);

        symbol = quoteSupport.requireSupported(symbol);

        final String supported = symbol;
        return cached("history:v1:" + symbol + ":range:" + range + ":" + LocalDate.now(java.time.ZoneId.of("America/Sao_Paulo")),
                () -> mapHistoricalQuotes(supported, brapiClient.getHistoricalQuotes(supported, range, DAILY_INTERVAL)));

    }

    public List<HistoricalQuote> getHistoricalQuotes(
            String symbol,
            LocalDate startDate,
            LocalDate endDate,
            PortfolioHistoryGranularity granularity) {

        if (startDate == null || endDate == null) {
            throw new IllegalArgumentException(
                    "Datas inicial e final são obrigatórias"
            );
        }

        if (endDate.isBefore(startDate)) {
            throw new IllegalArgumentException(
                    "Data final não pode ser anterior à data inicial"
            );
        }

        symbol = quoteSupport.requireSupported(symbol);

        final String supported = symbol;
        return cached("history:v1:" + symbol + ":dates:" + startDate + ":" + endDate,
                () -> mapHistoricalQuotes(supported, brapiClient.getHistoricalQuotes(supported, startDate, endDate, DAILY_INTERVAL)));

    }

    private List<HistoricalQuote> cached(String key, Supplier<List<HistoricalQuote>> load) {
        if (cache == null) return load.get();
        // Dashboard sections can request the same uncached range concurrently.
        // Share only that in-flight load; Redis remains the cache and failures are never retained.
        var pending = new CompletableFuture<List<HistoricalQuote>>();
        var existing = inFlight.putIfAbsent(key, pending);
        if (existing != null) return awaitHistory(existing);
        try {
            var result = loadCached(key, load);
            pending.complete(result);
            return result;
        } catch (RuntimeException | Error failure) {
            pending.completeExceptionally(failure);
            throw failure;
        } finally {
            inFlight.remove(key, pending);
        }
    }

    private List<HistoricalQuote> awaitHistory(CompletableFuture<List<HistoricalQuote>> pending) {
        long remaining = IntegrationBudgetFilter.remainingNanos(
                TimeUnit.MILLISECONDS.toNanos(properties.getBrapi().getOperationTimeoutMs()));
        if (remaining <= 0) throw new ExternalIntegrationException(504, "request_budget");
        try {
            return pending.get(remaining, TimeUnit.NANOSECONDS);
        } catch (TimeoutException timeout) {
            // One caller's deadline must not cancel another caller's request.
            throw new ExternalIntegrationException(504, "history_wait_timeout");
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new ExternalIntegrationException(503, "interrupted");
        } catch (ExecutionException failed) {
            if (failed.getCause() instanceof RuntimeException failure) throw failure;
            if (failed.getCause() instanceof Error error) throw error;
            throw new ExternalIntegrationException(502, "invalid_history");
        }
    }

    private List<HistoricalQuote> loadCached(String key, Supplier<List<HistoricalQuote>> load) {
        String value = cache.get(key);
        if (value != null) {
            try {
                HistoricalQuote[] points = json.readValue(value, HistoricalQuote[].class);
                if (points != null && points.length > 0 && Arrays.stream(points).allMatch(p ->
                        p != null && p.date() != null && p.closePrice() != null && p.closePrice().signum() >= 0))
                    return List.of(points);
            } catch (tools.jackson.core.JacksonException ignored) { /* Miss on invalid cached JSON. */ }
            cache.invalidEntry();
        }
        List<HistoricalQuote> points = load.get();
        if (!points.isEmpty()) cache.put(key, json.writeValueAsString(points), Duration.ofSeconds(properties.getHistoryTtlSeconds()));
        return points;
    }

    private List<HistoricalQuote> mapHistoricalQuotes(
            String symbol,
            BrapiHistoricalResponse response) {

        if (response == null || response.results() == null) {
            throw new IllegalStateException("Resposta histórica inválida do provedor de cotações");
        }
        if (response.results().isEmpty()) {

            throw new QuoteNotFoundException(symbol);
        }

        var result =
                response.results().getFirst();

        if (result == null || result.data() == null || result.data().historicalDataPrice() == null
                || result.data().historicalDataPrice().stream()
                        .anyMatch(price -> price == null || price.date() == null || price.close() == null || price.close().signum() < 0)) {
            throw new IllegalStateException("Resposta histórica inválida do provedor de cotações");
        }
        if (result.data().historicalDataPrice().isEmpty()) {

            throw new QuoteNotFoundException(symbol);
        }

        return result.data()
                .historicalDataPrice()
                .stream()
                .map(
                        historicalQuoteMapper
                                ::toHistoricalQuote
                )
                .toList();
    }
}
