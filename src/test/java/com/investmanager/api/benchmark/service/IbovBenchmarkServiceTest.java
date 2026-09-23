package com.investmanager.api.benchmark.service;

import com.investmanager.api.benchmark.entity.BenchmarkQuote;
import com.investmanager.api.benchmark.model.BenchmarkType;
import com.investmanager.api.benchmark.repository.BenchmarkQuoteRepository;
import com.investmanager.api.quote.client.BrapiClient;
import com.investmanager.api.quote.client.dto.BrapiIndexHistoricalPoint;
import com.investmanager.api.quote.client.dto.BrapiIndexHistoricalResponse;
import com.investmanager.api.quote.client.dto.BrapiIndexHistoricalResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IbovBenchmarkServiceTest {

    private static final ZoneId SAO_PAULO_ZONE =
            ZoneId.of("America/Sao_Paulo");

    private static final Instant FIXED_INSTANT =
            Instant.parse("2026-09-22T15:00:00Z");

    @Mock
    private BrapiClient brapiClient;

    @Mock
    private BenchmarkQuoteRepository benchmarkQuoteRepository;

    private IbovBenchmarkService service;

    @BeforeEach
    void setUp() {

        Clock fixedClock =
                Clock.fixed(
                        FIXED_INSTANT,
                        SAO_PAULO_ZONE
                );

        service =
                new IbovBenchmarkService(
                        brapiClient,
                        benchmarkQuoteRepository,
                        fixedClock
                );
    }

    @Test
    void shouldLoadEntirePeriodWhenDatabaseIsEmpty() {

        LocalDate startDate =
                LocalDate.of(2026, 9, 1);

        LocalDate endDate =
                LocalDate.of(2026, 9, 3);

        when(benchmarkQuoteRepository
                .findTopByBenchmarkOrderByDateAsc(
                        BenchmarkType.IBOV
                ))
                .thenReturn(Optional.empty());

        when(benchmarkQuoteRepository
                .findTopByBenchmarkOrderByDateDesc(
                        BenchmarkType.IBOV
                ))
                .thenReturn(Optional.empty());

        BrapiIndexHistoricalResponse response =
                createResponse(
                        LocalDate.of(2026, 9, 1),
                        new BigDecimal("141000.00")
                );

        when(brapiClient.getIndexHistoricalQuotes(
                "^BVSP",
                "1mo",
                "1d"
        )).thenReturn(response);

        when(benchmarkQuoteRepository
                .findByBenchmarkAndDate(
                        BenchmarkType.IBOV,
                        LocalDate.of(2026, 9, 1)
                ))
                .thenReturn(Optional.empty());

        List<BenchmarkQuote> expected =
                List.of(
                        new BenchmarkQuote(
                                BenchmarkType.IBOV,
                                LocalDate.of(2026, 9, 1),
                                new BigDecimal("141000.00")
                        )
                );

        when(benchmarkQuoteRepository
                .findByBenchmarkAndDateBetweenOrderByDateAsc(
                        BenchmarkType.IBOV,
                        startDate,
                        endDate
                ))
                .thenReturn(expected);

        List<BenchmarkQuote> result =
                service.load(
                        startDate,
                        endDate
                );

        verify(brapiClient)
                .getIndexHistoricalQuotes(
                        "^BVSP",
                        "1mo",
                        "1d"
                );

        ArgumentCaptor<BenchmarkQuote> captor =
                ArgumentCaptor.forClass(
                        BenchmarkQuote.class
                );

        verify(benchmarkQuoteRepository)
                .save(captor.capture());

        BenchmarkQuote savedQuote =
                captor.getValue();

        assertThat(savedQuote.getBenchmark())
                .isEqualTo(BenchmarkType.IBOV);

        assertThat(savedQuote.getDate())
                .isEqualTo(
                        LocalDate.of(2026, 9, 1)
                );

        assertThat(savedQuote.getValue())
                .isEqualByComparingTo(
                        "141000.00"
                );

        assertThat(result)
                .hasSize(1);
    }

    @Test
    void shouldNotCallBrapiWhenRequestedPeriodIsAlreadyStored() {

        LocalDate startDate =
                LocalDate.of(2026, 9, 1);

        LocalDate endDate =
                LocalDate.of(2026, 9, 10);

        BenchmarkQuote firstQuote =
                new BenchmarkQuote(
                        BenchmarkType.IBOV,
                        LocalDate.of(2026, 8, 1),
                        new BigDecimal("135000.00")
                );

        BenchmarkQuote lastQuote =
                new BenchmarkQuote(
                        BenchmarkType.IBOV,
                        LocalDate.of(2026, 9, 15),
                        new BigDecimal("145000.00")
                );

        when(benchmarkQuoteRepository
                .findTopByBenchmarkOrderByDateAsc(
                        BenchmarkType.IBOV
                ))
                .thenReturn(
                        Optional.of(firstQuote)
                );

        when(benchmarkQuoteRepository
                .findTopByBenchmarkOrderByDateDesc(
                        BenchmarkType.IBOV
                ))
                .thenReturn(
                        Optional.of(lastQuote)
                );

        when(benchmarkQuoteRepository
                .findByBenchmarkAndDateBetweenOrderByDateAsc(
                        BenchmarkType.IBOV,
                        startDate,
                        endDate
                ))
                .thenReturn(List.of());

        service.load(
                startDate,
                endDate
        );

        verifyNoInteractions(brapiClient);
    }

    @Test
    void shouldFetchOnlyPeriodAfterLastStoredQuote() {

        LocalDate startDate =
                LocalDate.of(2026, 9, 1);

        LocalDate endDate =
                LocalDate.of(2026, 9, 10);

        BenchmarkQuote firstQuote =
                new BenchmarkQuote(
                        BenchmarkType.IBOV,
                        LocalDate.of(2026, 9, 1),
                        new BigDecimal("140000.00")
                );

        BenchmarkQuote lastQuote =
                new BenchmarkQuote(
                        BenchmarkType.IBOV,
                        LocalDate.of(2026, 9, 5),
                        new BigDecimal("142000.00")
                );

        when(benchmarkQuoteRepository
                .findTopByBenchmarkOrderByDateAsc(
                        BenchmarkType.IBOV
                ))
                .thenReturn(
                        Optional.of(firstQuote)
                );

        when(benchmarkQuoteRepository
                .findTopByBenchmarkOrderByDateDesc(
                        BenchmarkType.IBOV
                ))
                .thenReturn(
                        Optional.of(lastQuote)
                );

        when(brapiClient.getIndexHistoricalQuotes(
                "^BVSP",
                "1mo",
                "1d"
        )).thenReturn(
                new BrapiIndexHistoricalResponse(
                        List.of()
                )
        );

        when(benchmarkQuoteRepository
                .findByBenchmarkAndDateBetweenOrderByDateAsc(
                        BenchmarkType.IBOV,
                        startDate,
                        endDate
                ))
                .thenReturn(List.of());

        service.load(
                startDate,
                endDate
        );

        verify(brapiClient)
                .getIndexHistoricalQuotes(
                        "^BVSP",
                        "1mo",
                        "1d"
                );
    }

    @Test
    void shouldConvertUnixTimestampToSaoPauloLocalDate() {

        LocalDate quoteDate =
                LocalDate.of(2026, 9, 2);

        LocalDate startDate =
                quoteDate;

        LocalDate endDate =
                quoteDate;

        when(benchmarkQuoteRepository
                .findTopByBenchmarkOrderByDateAsc(
                        BenchmarkType.IBOV
                ))
                .thenReturn(Optional.empty());

        when(benchmarkQuoteRepository
                .findTopByBenchmarkOrderByDateDesc(
                        BenchmarkType.IBOV
                ))
                .thenReturn(Optional.empty());

        when(brapiClient.getIndexHistoricalQuotes(
                "^BVSP",
                "1mo",
                "1d"
        )).thenReturn(
                createResponse(
                        quoteDate,
                        new BigDecimal(
                                "143500.75"
                        )
                )
        );

        when(benchmarkQuoteRepository
                .findByBenchmarkAndDate(
                        eq(BenchmarkType.IBOV),
                        any(LocalDate.class)
                ))
                .thenReturn(Optional.empty());

        when(benchmarkQuoteRepository
                .findByBenchmarkAndDateBetweenOrderByDateAsc(
                        BenchmarkType.IBOV,
                        startDate,
                        endDate
                ))
                .thenReturn(List.of());

        service.load(
                startDate,
                endDate
        );

        ArgumentCaptor<BenchmarkQuote> captor =
                ArgumentCaptor.forClass(
                        BenchmarkQuote.class
                );

        verify(benchmarkQuoteRepository)
                .save(captor.capture());

        assertThat(captor.getValue().getDate())
                .isEqualTo(quoteDate);

        assertThat(captor.getValue().getValue())
                .isEqualByComparingTo(
                        "143500.75"
                );
    }

    @Test
    void shouldRequestLongEnoughRangeForHistoricalPeriod() {

        LocalDate startDate =
                LocalDate.of(2020, 1, 1);

        LocalDate endDate =
                LocalDate.of(2020, 1, 31);

        when(benchmarkQuoteRepository
                .findTopByBenchmarkOrderByDateAsc(
                        BenchmarkType.IBOV
                ))
                .thenReturn(Optional.empty());

        when(benchmarkQuoteRepository
                .findTopByBenchmarkOrderByDateDesc(
                        BenchmarkType.IBOV
                ))
                .thenReturn(Optional.empty());

        when(brapiClient.getIndexHistoricalQuotes(
                "^BVSP",
                "10y",
                "1d"
        )).thenReturn(
                new BrapiIndexHistoricalResponse(
                        List.of()
                )
        );

        when(benchmarkQuoteRepository
                .findByBenchmarkAndDateBetweenOrderByDateAsc(
                        BenchmarkType.IBOV,
                        startDate,
                        endDate
                ))
                .thenReturn(List.of());

        service.load(
                startDate,
                endDate
        );

        verify(brapiClient)
                .getIndexHistoricalQuotes(
                        "^BVSP",
                        "10y",
                        "1d"
                );
    }

    private BrapiIndexHistoricalResponse createResponse(
            LocalDate date,
            BigDecimal close) {

        long timestamp =
                date.atStartOfDay(
                                SAO_PAULO_ZONE
                        )
                        .toEpochSecond();

        BrapiIndexHistoricalPoint point =
                new BrapiIndexHistoricalPoint(
                        timestamp,
                        null,
                        null,
                        null,
                        close,
                        null,
                        null
                );

        BrapiIndexHistoricalResult result =
                new BrapiIndexHistoricalResult(
                        "^BVSP",
                        List.of(point)
                );

        return new BrapiIndexHistoricalResponse(
                List.of(result)
        );
    }
}