package com.investmanager.api.benchmark.service;

import com.investmanager.api.benchmark.entity.BenchmarkQuote;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.sql.Date;
import java.util.Comparator;
import java.util.List;

@Service
public class BenchmarkQuoteWriter {
    private final JdbcTemplate jdbc;
    public BenchmarkQuoteWriter(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Transactional(propagation = Propagation.REQUIRES_NEW, timeout = 10)
    public void insertMissing(List<BenchmarkQuote> quotes) {
        if (quotes.isEmpty()) return;
        // Deterministic ordering prevents opposite lock acquisition by concurrent synchronizations.
        var ordered = quotes.stream().sorted(Comparator.comparing(BenchmarkQuote::getBenchmark)
                .thenComparing(BenchmarkQuote::getDate)).toList();
        jdbc.batchUpdate("""
                INSERT INTO benchmark_quotes (benchmark, quote_date, value) VALUES (?, ?, ?)
                ON CONFLICT (benchmark, quote_date) DO NOTHING
                """, ordered, 250, (ps, q) -> {
            ps.setString(1, q.getBenchmark().name());
            ps.setDate(2, Date.valueOf(q.getDate()));
            ps.setBigDecimal(3, q.getValue());
        });
    }
}
