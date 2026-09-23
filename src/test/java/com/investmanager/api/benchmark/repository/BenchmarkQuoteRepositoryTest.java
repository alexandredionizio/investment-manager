package com.investmanager.api.benchmark.repository;

import com.investmanager.api.benchmark.entity.BenchmarkQuote;
import com.investmanager.api.benchmark.model.BenchmarkType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Testcontainers
class BenchmarkQuoteRepositoryTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres =
            new PostgreSQLContainer("postgres:17-alpine");

    @Autowired
    private BenchmarkQuoteRepository repository;

    @Test
    void shouldFindBenchmarkQuotesBetweenDatesOrderedByDate() {

        repository.save(new BenchmarkQuote(
                BenchmarkType.IBOV,
                LocalDate.of(2026, 9, 17),
                new BigDecimal("145000.00000000")
        ));

        repository.save(new BenchmarkQuote(
                BenchmarkType.IBOV,
                LocalDate.of(2026, 9, 15),
                new BigDecimal("143000.00000000")
        ));

        repository.save(new BenchmarkQuote(
                BenchmarkType.IBOV,
                LocalDate.of(2026, 9, 16),
                new BigDecimal("144000.00000000")
        ));

        repository.save(new BenchmarkQuote(
                BenchmarkType.CDI,
                LocalDate.of(2026, 9, 16),
                new BigDecimal("0.05500000")
        ));

        List<BenchmarkQuote> quotes =
                repository.findByBenchmarkAndDateBetweenOrderByDateAsc(
                        BenchmarkType.IBOV,
                        LocalDate.of(2026, 9, 15),
                        LocalDate.of(2026, 9, 17)
                );

        assertThat(quotes).hasSize(3);

        assertThat(quotes)
                .extracting(BenchmarkQuote::getDate)
                .containsExactly(
                        LocalDate.of(2026, 9, 15),
                        LocalDate.of(2026, 9, 16),
                        LocalDate.of(2026, 9, 17)
                );
    }

    @Test
    void shouldFindBenchmarkQuoteByBenchmarkAndDate() {

        repository.save(new BenchmarkQuote(
                BenchmarkType.CDI,
                LocalDate.of(2026, 9, 21),
                new BigDecimal("0.05500000")
        ));

        Optional<BenchmarkQuote> quote =
                repository.findByBenchmarkAndDate(
                        BenchmarkType.CDI,
                        LocalDate.of(2026, 9, 21)
                );

        assertThat(quote).isPresent();

        assertThat(quote.get().getBenchmark())
                .isEqualTo(BenchmarkType.CDI);

        assertThat(quote.get().getDate())
                .isEqualTo(LocalDate.of(2026, 9, 21));

        assertThat(quote.get().getValue())
                .isEqualByComparingTo("0.05500000");
    }
}