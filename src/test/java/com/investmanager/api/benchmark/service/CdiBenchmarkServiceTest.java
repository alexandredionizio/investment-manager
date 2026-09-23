package com.investmanager.api.benchmark.service;

import com.investmanager.api.benchmark.client.bcb.BcbClient;
import com.investmanager.api.benchmark.client.bcb.BcbSeriesPointResponse;
import com.investmanager.api.benchmark.entity.BenchmarkQuote;
import com.investmanager.api.benchmark.model.BenchmarkType;
import com.investmanager.api.benchmark.repository.BenchmarkQuoteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CdiBenchmarkServiceTest {

    @Mock
    private BcbClient bcbClient;

    @Mock
    private BenchmarkQuoteRepository benchmarkQuoteRepository;

    private CdiBenchmarkService service;

    @BeforeEach
    void setUp() {
        service = new CdiBenchmarkService(
                bcbClient,
                benchmarkQuoteRepository
        );
    }

    @Test
    void shouldLoadEntirePeriodWhenDatabaseIsEmpty() {

        LocalDate startDate =
                LocalDate.of(2026, 9, 21);

        LocalDate endDate =
                LocalDate.of(2026, 9, 22);

        when(benchmarkQuoteRepository
                .findTopByBenchmarkOrderByDateAsc(BenchmarkType.CDI))
                .thenReturn(Optional.empty());

        when(benchmarkQuoteRepository
                .findTopByBenchmarkOrderByDateDesc(BenchmarkType.CDI))
                .thenReturn(Optional.empty());

        when(bcbClient.getSeries(
                12,
                startDate,
                endDate
        )).thenReturn(List.of(
                new BcbSeriesPointResponse(
                        "21/09/2026",
                        "0.055000"
                ),
                new BcbSeriesPointResponse(
                        "22/09/2026",
                        "0.055100"
                )
        ));

        when(benchmarkQuoteRepository.findByBenchmarkAndDate(
                eq(BenchmarkType.CDI),
                any(LocalDate.class)
        )).thenReturn(Optional.empty());

        when(benchmarkQuoteRepository
                .findByBenchmarkAndDateBetweenOrderByDateAsc(
                        BenchmarkType.CDI,
                        startDate,
                        endDate
                ))
                .thenReturn(List.of(
                        quote(
                                LocalDate.of(2026, 9, 21),
                                "0.055000"
                        ),
                        quote(
                                LocalDate.of(2026, 9, 22),
                                "0.055100"
                        )
                ));

        List<BenchmarkQuote> result =
                service.load(startDate, endDate);

        assertEquals(2, result.size());

        verify(bcbClient).getSeries(
                12,
                startDate,
                endDate
        );

        verify(benchmarkQuoteRepository, times(2))
                .save(any(BenchmarkQuote.class));
    }

    @Test
    void shouldNotCallBcbWhenRequestedPeriodIsAlreadyStored() {

        LocalDate storedStart =
                LocalDate.of(2026, 9, 1);

        LocalDate storedEnd =
                LocalDate.of(2026, 9, 30);

        LocalDate requestedStart =
                LocalDate.of(2026, 9, 10);

        LocalDate requestedEnd =
                LocalDate.of(2026, 9, 20);

        when(benchmarkQuoteRepository
                .findTopByBenchmarkOrderByDateAsc(BenchmarkType.CDI))
                .thenReturn(Optional.of(
                        quote(storedStart, "0.055000")
                ));

        when(benchmarkQuoteRepository
                .findTopByBenchmarkOrderByDateDesc(BenchmarkType.CDI))
                .thenReturn(Optional.of(
                        quote(storedEnd, "0.055100")
                ));

        when(benchmarkQuoteRepository
                .findByBenchmarkAndDateBetweenOrderByDateAsc(
                        BenchmarkType.CDI,
                        requestedStart,
                        requestedEnd
                ))
                .thenReturn(List.of(
                        quote(
                                LocalDate.of(2026, 9, 10),
                                "0.055050"
                        )
                ));

        List<BenchmarkQuote> result =
                service.load(
                        requestedStart,
                        requestedEnd
                );

        assertEquals(1, result.size());

        verifyNoInteractions(bcbClient);

        verify(
                benchmarkQuoteRepository,
                never()
        ).save(any(BenchmarkQuote.class));
    }

    @Test
    void shouldFetchOnlyPeriodAfterLastStoredQuote() {

        LocalDate storedStart =
                LocalDate.of(2026, 9, 1);

        LocalDate storedEnd =
                LocalDate.of(2026, 9, 20);

        LocalDate requestedEnd =
                LocalDate.of(2026, 9, 22);

        when(benchmarkQuoteRepository
                .findTopByBenchmarkOrderByDateAsc(BenchmarkType.CDI))
                .thenReturn(Optional.of(
                        quote(storedStart, "0.055000")
                ));

        when(benchmarkQuoteRepository
                .findTopByBenchmarkOrderByDateDesc(BenchmarkType.CDI))
                .thenReturn(Optional.of(
                        quote(storedEnd, "0.055000")
                ));

        when(bcbClient.getSeries(
                12,
                LocalDate.of(2026, 9, 21),
                requestedEnd
        )).thenReturn(List.of(
                new BcbSeriesPointResponse(
                        "21/09/2026",
                        "0.055050"
                ),
                new BcbSeriesPointResponse(
                        "22/09/2026",
                        "0.055100"
                )
        ));

        when(benchmarkQuoteRepository.findByBenchmarkAndDate(
                eq(BenchmarkType.CDI),
                any(LocalDate.class)
        )).thenReturn(Optional.empty());

        when(benchmarkQuoteRepository
                .findByBenchmarkAndDateBetweenOrderByDateAsc(
                        BenchmarkType.CDI,
                        storedStart,
                        requestedEnd
                ))
                .thenReturn(List.of(
                        quote(storedStart, "0.055000"),
                        quote(storedEnd, "0.055000"),
                        quote(
                                LocalDate.of(2026, 9, 21),
                                "0.055050"
                        ),
                        quote(
                                requestedEnd,
                                "0.055100"
                        )
                ));

        List<BenchmarkQuote> result =
                service.load(
                        storedStart,
                        requestedEnd
                );

        assertEquals(4, result.size());

        verify(bcbClient).getSeries(
                12,
                LocalDate.of(2026, 9, 21),
                LocalDate.of(2026, 9, 22)
        );

        verify(benchmarkQuoteRepository, times(2))
                .save(any(BenchmarkQuote.class));
    }

    private BenchmarkQuote quote(
            LocalDate date,
            String value) {

        return new BenchmarkQuote(
                BenchmarkType.CDI,
                date,
                new BigDecimal(value)
        );
    }
}