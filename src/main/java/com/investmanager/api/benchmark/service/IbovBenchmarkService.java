package com.investmanager.api.benchmark.service;

import com.investmanager.api.benchmark.entity.BenchmarkQuote;
import com.investmanager.api.benchmark.model.BenchmarkType;
import com.investmanager.api.benchmark.repository.BenchmarkQuoteRepository;
import com.investmanager.api.quote.client.BrapiClient;
import com.investmanager.api.quote.client.dto.BrapiIndexHistoricalPoint;
import com.investmanager.api.quote.client.dto.BrapiIndexHistoricalResponse;
import com.investmanager.api.quote.client.dto.BrapiIndexHistoricalResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

@Service
public class IbovBenchmarkService {

    private static final String IBOV_SYMBOL = "^BVSP";
    private static final String DAILY_INTERVAL = "1d";

    private static final ZoneId SAO_PAULO_ZONE =
            ZoneId.of("America/Sao_Paulo");

    private final BrapiClient brapiClient;
    private final BenchmarkQuoteRepository benchmarkQuoteRepository;
    private final Clock clock;

    @Autowired
    public IbovBenchmarkService(
            BrapiClient brapiClient,
            BenchmarkQuoteRepository benchmarkQuoteRepository) {

        this(
                brapiClient,
                benchmarkQuoteRepository,
                Clock.system(SAO_PAULO_ZONE)
        );
    }

    IbovBenchmarkService(
            BrapiClient brapiClient,
            BenchmarkQuoteRepository benchmarkQuoteRepository,
            Clock clock) {

        this.brapiClient = brapiClient;
        this.benchmarkQuoteRepository = benchmarkQuoteRepository;
        this.clock = clock;
    }

    @Transactional
    public List<BenchmarkQuote> load(
            LocalDate startDate,
            LocalDate endDate) {

        Optional<BenchmarkQuote> firstStoredQuote =
                benchmarkQuoteRepository
                        .findTopByBenchmarkOrderByDateAsc(
                                BenchmarkType.IBOV
                        );

        Optional<BenchmarkQuote> lastStoredQuote =
                benchmarkQuoteRepository
                        .findTopByBenchmarkOrderByDateDesc(
                                BenchmarkType.IBOV
                        );

        if (firstStoredQuote.isEmpty()
                || lastStoredQuote.isEmpty()) {

            fetchAndSave(startDate, endDate);

        } else {

            LocalDate firstStoredDate =
                    firstStoredQuote.get().getDate();

            LocalDate lastStoredDate =
                    lastStoredQuote.get().getDate();

            if (startDate.isBefore(firstStoredDate)) {

                fetchAndSave(
                        startDate,
                        firstStoredDate.minusDays(1)
                );
            }

            if (endDate.isAfter(lastStoredDate)) {

                fetchAndSave(
                        lastStoredDate.plusDays(1),
                        endDate
                );
            }
        }

        return benchmarkQuoteRepository
                .findByBenchmarkAndDateBetweenOrderByDateAsc(
                        BenchmarkType.IBOV,
                        startDate,
                        endDate
                );
    }

    private void fetchAndSave(
            LocalDate startDate,
            LocalDate endDate) {

        if (startDate.isAfter(endDate)) {
            return;
        }

        String range =
                determineRange(startDate);

        BrapiIndexHistoricalResponse response =
                brapiClient.getIndexHistoricalQuotes(
                        IBOV_SYMBOL,
                        range,
                        DAILY_INTERVAL
                );

        if (response == null
                || response.results() == null
                || response.results().isEmpty()) {
            return;
        }

        BrapiIndexHistoricalResult result =
                response.results().getFirst();

        if (result.historicalDataPrice() == null) {
            return;
        }

        for (BrapiIndexHistoricalPoint price
                : result.historicalDataPrice()) {

            if (price.date() == null
                    || price.close() == null) {
                continue;
            }

            LocalDate date =
                    Instant.ofEpochSecond(price.date())
                            .atZone(SAO_PAULO_ZONE)
                            .toLocalDate();

            if (date.isBefore(startDate)
                    || date.isAfter(endDate)) {
                continue;
            }

            BigDecimal value =
                    price.close();

            if (benchmarkQuoteRepository
                    .findByBenchmarkAndDate(
                            BenchmarkType.IBOV,
                            date
                    )
                    .isEmpty()) {

                benchmarkQuoteRepository.save(
                        new BenchmarkQuote(
                                BenchmarkType.IBOV,
                                date,
                                value
                        )
                );
            }
        }
    }

    private String determineRange(
            LocalDate startDate) {

        LocalDate today =
                LocalDate.now(clock);

        long daysFromStartDate =
                ChronoUnit.DAYS.between(
                        startDate,
                        today
                ) + 1;

        if (daysFromStartDate <= 5) {
            return "5d";
        }

        if (daysFromStartDate <= 30) {
            return "1mo";
        }

        if (daysFromStartDate <= 90) {
            return "3mo";
        }

        if (daysFromStartDate <= 180) {
            return "6mo";
        }

        if (daysFromStartDate <= 365) {
            return "1y";
        }

        if (daysFromStartDate <= 730) {
            return "2y";
        }

        if (daysFromStartDate <= 1825) {
            return "5y";
        }

        if (daysFromStartDate <= 3650) {
            return "10y";
        }

        return "max";
    }
}