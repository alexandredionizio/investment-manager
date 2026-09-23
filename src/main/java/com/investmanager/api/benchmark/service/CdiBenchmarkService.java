package com.investmanager.api.benchmark.service;

import com.investmanager.api.benchmark.client.bcb.BcbClient;
import com.investmanager.api.benchmark.client.bcb.BcbSeriesPointResponse;
import com.investmanager.api.benchmark.entity.BenchmarkQuote;
import com.investmanager.api.benchmark.model.BenchmarkType;
import com.investmanager.api.benchmark.repository.BenchmarkQuoteRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

@Service
public class CdiBenchmarkService {

    private static final int CDI_SERIES_CODE = 12;

    private static final DateTimeFormatter BCB_DATE_FORMATTER =
            DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final BcbClient bcbClient;
    private final BenchmarkQuoteRepository benchmarkQuoteRepository;

    public CdiBenchmarkService(
            BcbClient bcbClient,
            BenchmarkQuoteRepository benchmarkQuoteRepository) {

        this.bcbClient = bcbClient;
        this.benchmarkQuoteRepository = benchmarkQuoteRepository;
    }

    @Transactional
    public List<BenchmarkQuote> load(
            LocalDate startDate,
            LocalDate endDate) {

        Optional<BenchmarkQuote> firstStoredQuote =
                benchmarkQuoteRepository
                        .findTopByBenchmarkOrderByDateAsc(
                                BenchmarkType.CDI
                        );

        Optional<BenchmarkQuote> lastStoredQuote =
                benchmarkQuoteRepository
                        .findTopByBenchmarkOrderByDateDesc(
                                BenchmarkType.CDI
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

                LocalDate missingEndDate =
                        firstStoredDate.minusDays(1);

                fetchAndSave(
                        startDate,
                        missingEndDate
                );
            }

            if (endDate.isAfter(lastStoredDate)) {

                LocalDate missingStartDate =
                        lastStoredDate.plusDays(1);

                fetchAndSave(
                        missingStartDate,
                        endDate
                );
            }
        }

        return benchmarkQuoteRepository
                .findByBenchmarkAndDateBetweenOrderByDateAsc(
                        BenchmarkType.CDI,
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

        List<BcbSeriesPointResponse> response =
                bcbClient.getSeries(
                        CDI_SERIES_CODE,
                        startDate,
                        endDate
                );

        for (BcbSeriesPointResponse point : response) {

            LocalDate date =
                    LocalDate.parse(
                            point.date(),
                            BCB_DATE_FORMATTER
                    );

            BigDecimal value =
                    new BigDecimal(point.value());

            if (benchmarkQuoteRepository
                    .findByBenchmarkAndDate(
                            BenchmarkType.CDI,
                            date
                    )
                    .isEmpty()) {

                benchmarkQuoteRepository.save(
                        new BenchmarkQuote(
                                BenchmarkType.CDI,
                                date,
                                value
                        )
                );
            }
        }
    }
}