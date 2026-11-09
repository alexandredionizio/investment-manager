package com.investmanager.api.benchmark.service;

import com.investmanager.api.benchmark.entity.BenchmarkQuote;
import com.investmanager.api.benchmark.model.BenchmarkType;
import com.investmanager.api.benchmark.repository.BenchmarkQuoteRepository;
import com.investmanager.api.shared.integration.MarketDataCache;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import static org.mockito.Mockito.*;

public class BenchmarkHistoryFixture {
    public final BenchmarkQuoteRepository repository = mock(BenchmarkQuoteRepository.class);
    public final BenchmarkQuoteWriter writer = mock(BenchmarkQuoteWriter.class);
    public final MarketDataCache cache = mock(MarketDataCache.class);
    public final Map<String, BenchmarkQuote> rows = new ConcurrentHashMap<>();
    public final Map<String, String> cached = new ConcurrentHashMap<>();
    public final Clock clock = Clock.fixed(Instant.parse("2026-11-06T15:00:00Z"), ZoneId.of("America/Sao_Paulo"));
    public final BenchmarkHistorySynchronizer synchronizer = new BenchmarkHistorySynchronizer(repository, writer, cache, clock);

    public BenchmarkHistoryFixture() {
        lenient().when(repository.findByBenchmarkAndDateBetweenOrderByDateAsc(any(), any(), any())).thenAnswer(call -> {
            BenchmarkType type = call.getArgument(0);
            LocalDate from = call.getArgument(1), to = call.getArgument(2);
            return rows.values().stream().filter(q -> q.getBenchmark() == type)
                    .filter(q -> !q.getDate().isBefore(from) && !q.getDate().isAfter(to))
                    .sorted(Comparator.comparing(BenchmarkQuote::getDate)).toList();
        });
        lenient().doAnswer(call -> { for (BenchmarkQuote q : (List<BenchmarkQuote>) call.getArgument(0)) put(q); return null; })
                .when(writer).insertMissing(any());
        lenient().when(cache.get(anyString())).thenAnswer(call -> cached.get(call.<String>getArgument(0)));
        lenient().doAnswer(call -> { cached.put(call.getArgument(0), call.getArgument(1)); return null; })
                .when(cache).put(anyString(), anyString(), any());
    }
    public void put(BenchmarkQuote q) { rows.putIfAbsent(q.getBenchmark()+":"+q.getDate(), q); }
    public static BenchmarkQuote q(BenchmarkType type, String date, String value) {
        return new BenchmarkQuote(type, LocalDate.parse(date), new BigDecimal(value));
    }
}
