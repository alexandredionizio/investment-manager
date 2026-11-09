package com.investmanager.api.benchmark.service;

import com.investmanager.api.benchmark.entity.BenchmarkQuote;
import com.investmanager.api.benchmark.exception.BenchmarkHistoryIncompleteException;
import com.investmanager.api.benchmark.model.BenchmarkType;
import com.investmanager.api.benchmark.repository.BenchmarkQuoteRepository;
import com.investmanager.api.shared.integration.ExternalIntegrationException;
import com.investmanager.api.shared.integration.MarketDataCache;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

/** Provider observations, never a shared holiday calendar, establish missing publications. */
@Service
public class BenchmarkHistorySynchronizer {
    private static final int MAX_FETCHES = 4;
    private final BenchmarkQuoteRepository repository;
    private final BenchmarkQuoteWriter writer;
    private final MarketDataCache cache;
    private final Clock clock;

    @Autowired
    public BenchmarkHistorySynchronizer(BenchmarkQuoteRepository repository, BenchmarkQuoteWriter writer,
                                        MarketDataCache cache) {
        this(repository, writer, cache, Clock.system(ZoneId.of("America/Sao_Paulo")));
    }

    BenchmarkHistorySynchronizer(BenchmarkQuoteRepository repository, BenchmarkQuoteWriter writer,
                                 MarketDataCache cache, Clock clock) {
        this.repository = repository;
        this.writer = writer;
        this.cache = cache;
        this.clock = clock;
    }

    // Suspend an ambient transaction as well: HTTP and Redis must not hold database locks.
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public List<BenchmarkQuote> load(BenchmarkType type, LocalDate start, LocalDate end,
                                    BiFunction<LocalDate, LocalDate, List<BenchmarkQuote>> source) {
        if (start == null || end == null || start.isAfter(end))
            throw new IllegalArgumentException("Intervalo de benchmark inválido");
        LocalDate today = LocalDate.now(clock);
        if (start.isAfter(today)) return List.of();
        LocalDate effectiveEnd = end.isAfter(today) ? today : end;
        var stored = index(type, repository.findByBenchmarkAndDateBetweenOrderByDateAsc(type, start, effectiveEnd));
        // Stable quarterly keys also reuse verified non-publication days when dashboard dates move.
        var quarters = BenchmarkHistoryGapDetector.candidateQuarters(start, effectiveEnd, stored.keySet());
        int fetches = 0;
        for (LocalDate from : quarters) {
            LocalDate to = from.plusMonths(3).minusDays(1);
            if (to.isAfter(today)) to = today;
            var existing = index(type, repository.findByBenchmarkAndDateBetweenOrderByDateAsc(type, from, to));
            String key = "benchmark:history:v1:" + type + ":" + from + ":" + to;
            var published = readCache(type, cache.get(key), from, to);
            // Cached absence must not contradict newer persisted publications.
            if (published != null && !published.keySet().containsAll(existing.keySet())) published = null;
            boolean fetched = published == null;
            if (fetched) {
                if (fetches++ == MAX_FETCHES)
                    throw new BenchmarkHistoryIncompleteException("Sincronização histórica de " + type
                            + " ainda não concluída. Limite de consultas atingido; tente novamente para continuar.");
                published = index(type, source.apply(from, to));
                if (published.keySet().stream().anyMatch(d -> d.isBefore(from) || d.isAfter(LocalDate.now(clock))))
                    throw invalid("invalid_benchmark_date");
                LocalDate windowEnd = to;
                if (published.keySet().stream().anyMatch(d -> d.isAfter(windowEnd)))
                    throw invalid("invalid_benchmark_date");
                if (!published.keySet().containsAll(existing.keySet()))
                    throw invalid("incomplete_benchmark_coverage");
            }
            // Validate the entire response before opening the short write transaction.
            var additions = published.values().stream().filter(q -> !existing.containsKey(q.getDate())).toList();
            if (!additions.isEmpty()) writer.insertMissing(additions);
            var persisted = index(type, repository.findByBenchmarkAndDateBetweenOrderByDateAsc(type, from, to));
            var missing = published.keySet().stream().filter(d -> !persisted.containsKey(d)).toList();
            if (!missing.isEmpty()) throw new BenchmarkHistoryIncompleteException(type, missing);
            // Never mark failed writes or truncated responses as verified absence.
            if (fetched) cache.put(key, encode(published.values()),
                    to.isBefore(today.minusDays(7)) ? Duration.ofHours(24) : Duration.ofMinutes(5));
        }
        return index(type, repository.findByBenchmarkAndDateBetweenOrderByDateAsc(type, start, effectiveEnd))
                .values().stream().toList();
    }

    private TreeMap<LocalDate, BenchmarkQuote> readCache(BenchmarkType type, String value, LocalDate from, LocalDate to) {
        if (value == null) return null;
        try {
            if (!value.startsWith("v1\n")) throw new IllegalArgumentException();
            var quotes = new ArrayList<BenchmarkQuote>();
            for (String line : value.substring(3).split("\n")) {
                if (line.isEmpty()) continue;
                String[] parts = line.split("=", -1);
                if (parts.length != 2) throw new IllegalArgumentException();
                LocalDate date = LocalDate.parse(parts[0]);
                if (date.isBefore(from) || date.isAfter(to)) throw new IllegalArgumentException();
                quotes.add(new BenchmarkQuote(type, date, new BigDecimal(parts[1])));
            }
            return index(type, quotes);
        } catch (RuntimeException invalidCache) {
            cache.invalidEntry();
            return null;
        }
    }

    static TreeMap<LocalDate, BenchmarkQuote> index(BenchmarkType type, List<BenchmarkQuote> quotes) {
        if (quotes == null) throw invalid("invalid_benchmark_history");
        var result = new TreeMap<LocalDate, BenchmarkQuote>();
        for (var q : quotes) {
            if (q == null || q.getBenchmark() != type || q.getDate() == null || q.getValue() == null
                    || (type == BenchmarkType.IBOV ? q.getValue().signum() <= 0 : q.getValue().compareTo(new BigDecimal("-100")) <= 0)
                    || q.getValue().stripTrailingZeros().scale() > 8
                    || q.getValue().abs().compareTo(new BigDecimal("100000000000")) >= 0)
                throw invalid("invalid_benchmark_history");
            var previous = result.putIfAbsent(q.getDate(), q);
            if (previous != null && previous.getValue().compareTo(q.getValue()) != 0)
                throw invalid("conflicting_benchmark_history");
        }
        return result;
    }

    private static String encode(Collection<BenchmarkQuote> quotes) {
        return "v1\n" + quotes.stream().map(q -> q.getDate() + "=" + q.getValue().toPlainString())
                .collect(Collectors.joining("\n"));
    }
    private static ExternalIntegrationException invalid(String category) {
        return new ExternalIntegrationException(502, category);
    }
}
