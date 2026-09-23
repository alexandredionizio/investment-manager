package com.investmanager.api.benchmark.repository;

import com.investmanager.api.benchmark.entity.BenchmarkQuote;
import com.investmanager.api.benchmark.model.BenchmarkType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface BenchmarkQuoteRepository
        extends JpaRepository<BenchmarkQuote, Long> {

    List<BenchmarkQuote> findByBenchmarkAndDateBetweenOrderByDateAsc(
            BenchmarkType benchmark,
            LocalDate startDate,
            LocalDate endDate
    );

    Optional<BenchmarkQuote> findByBenchmarkAndDate(
            BenchmarkType benchmark,
            LocalDate date
    );

    Optional<BenchmarkQuote> findTopByBenchmarkOrderByDateAsc(
            BenchmarkType benchmark
    );

    Optional<BenchmarkQuote> findTopByBenchmarkOrderByDateDesc(
            BenchmarkType benchmark
    );
}