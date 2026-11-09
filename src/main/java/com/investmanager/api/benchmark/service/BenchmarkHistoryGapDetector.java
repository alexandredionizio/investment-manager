package com.investmanager.api.benchmark.service;

import com.investmanager.api.benchmark.entity.BenchmarkQuote;
import com.investmanager.api.benchmark.exception.BenchmarkHistoryIncompleteException;
import com.investmanager.api.benchmark.model.BenchmarkType;
import com.investmanager.api.benchmark.repository.BenchmarkQuoteRepository;
import com.investmanager.api.shared.integration.ExternalIntegrationException;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.TreeMap;
import java.util.function.BiFunction;

/** Identifies candidate gaps; publication dates are confirmed independently for each provider. */
final class BenchmarkHistoryGapDetector {
    private final BenchmarkQuoteRepository repository;

    BenchmarkHistoryGapDetector(BenchmarkQuoteRepository repository) {
        this.repository = repository;
    }

    List<BenchmarkQuote> verify(BenchmarkType benchmark, LocalDate start, LocalDate end,
                               List<BenchmarkQuote> quotes,
                               BiFunction<LocalDate, LocalDate, List<BenchmarkQuote>> source) {
        var stored = index(benchmark, quotes);
        // Neighbours expose gaps even when the requested interval contains no stored rows.
        if (!stored.containsKey(start)) {
            repository.findTopByBenchmarkAndDateLessThanEqualOrderByDateDesc(benchmark, start)
                    .ifPresent(q -> stored.put(q.getDate(), q));
        }
        if (!stored.containsKey(end)) {
            repository.findTopByBenchmarkAndDateGreaterThanEqualOrderByDateAsc(benchmark, end)
                    .ifPresent(q -> stored.put(q.getDate(), q));
        }

        LocalDate verificationStart = null;
        LocalDate verificationEnd = null;
        LocalDate previous = null;
        for (LocalDate date : stored.keySet()) {
            if (previous != null) {
                LocalDate gapStart = previous.plusDays(1).isBefore(start) ? start : previous.plusDays(1);
                LocalDate gapEnd = date.minusDays(1).isAfter(end) ? end : date.minusDays(1);
                if (hasWeekday(gapStart, gapEnd)) {
                    if (verificationStart == null) verificationStart = previous;
                    verificationEnd = date;
                }
            }
            previous = date;
        }

        if (verificationStart != null) {
            var published = index(benchmark, source.apply(verificationStart, verificationEnd));
            // A truncated/empty provider range cannot establish that a gap is a holiday.
            if (!published.keySet().containsAll(stored.subMap(verificationStart, true, verificationEnd, true).keySet())) {
                throw new ExternalIntegrationException(502, "incomplete_benchmark_coverage");
            }
            var missing = published.keySet().stream()
                    .filter(date -> !date.isBefore(start) && !date.isAfter(end))
                    .filter(date -> !stored.containsKey(date))
                    .toList();
            if (!missing.isEmpty()) throw new BenchmarkHistoryIncompleteException(benchmark, missing);
        }

        return stored.subMap(start, true, end, true).values().stream().toList();
    }

    static java.util.SortedSet<LocalDate> candidateQuarters(LocalDate start, LocalDate end,
                                                           java.util.Set<LocalDate> storedDates) {
        var quarters = new java.util.TreeSet<LocalDate>();
        for (LocalDate date = start; !date.isAfter(end); date = date.plusDays(1)) {
            if (hasWeekday(date, date) && !storedDates.contains(date))
                quarters.add(LocalDate.of(date.getYear(), ((date.getMonthValue() - 1) / 3) * 3 + 1, 1));
        }
        return quarters;
    }

    private static boolean hasWeekday(LocalDate start, LocalDate end) {
        for (LocalDate date = start; !date.isAfter(end); date = date.plusDays(1)) {
            if (date.getDayOfWeek() != DayOfWeek.SATURDAY && date.getDayOfWeek() != DayOfWeek.SUNDAY) return true;
        }
        return false;
    }

    private static TreeMap<LocalDate, BenchmarkQuote> index(BenchmarkType benchmark, List<BenchmarkQuote> quotes) {
        if (quotes == null) throw new ExternalIntegrationException(502, "invalid_benchmark_history");
        var result = new TreeMap<LocalDate, BenchmarkQuote>();
        for (var quote : quotes) {
            if (quote == null || quote.getBenchmark() != benchmark || quote.getDate() == null || quote.getValue() == null) {
                throw new ExternalIntegrationException(502, "invalid_benchmark_history");
            }
            var previous = result.putIfAbsent(quote.getDate(), quote);
            if (previous != null && previous.getValue().compareTo(quote.getValue()) != 0) {
                throw new ExternalIntegrationException(502, "conflicting_benchmark_history");
            }
        }
        return result;
    }
}
