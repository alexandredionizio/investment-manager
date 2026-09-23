package com.investmanager.api.benchmark.entity;

import com.investmanager.api.benchmark.model.BenchmarkType;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(
        name = "benchmark_quotes",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_benchmark_quotes_benchmark_date",
                        columnNames = {"benchmark", "quote_date"}
                )
        }
)
public class BenchmarkQuote {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BenchmarkType benchmark;

    @Column(name = "quote_date", nullable = false)
    private LocalDate date;

    @Column(nullable = false, precision = 19, scale = 8)
    private BigDecimal value;

    protected BenchmarkQuote() {
    }

    public BenchmarkQuote(
            BenchmarkType benchmark,
            LocalDate date,
            BigDecimal value) {

        this.benchmark = benchmark;
        this.date = date;
        this.value = value;
    }

    public Long getId() {
        return id;
    }

    public BenchmarkType getBenchmark() {
        return benchmark;
    }

    public LocalDate getDate() {
        return date;
    }

    public BigDecimal getValue() {
        return value;
    }
}