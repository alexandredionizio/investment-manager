package com.investmanager.api.benchmark.service;

import com.investmanager.api.benchmark.client.bcb.BcbClient;
import com.investmanager.api.benchmark.client.bcb.BcbSeriesPointResponse;
import com.investmanager.api.benchmark.entity.BenchmarkQuote;
import com.investmanager.api.benchmark.model.BenchmarkType;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class CdiBenchmarkService {

    private static final int CDI_SERIES_CODE = 12;

    private static final DateTimeFormatter BCB_DATE_FORMATTER =
            DateTimeFormatter.ofPattern("dd/MM/uuuu").withResolverStyle(java.time.format.ResolverStyle.STRICT);

    private final BcbClient bcbClient;
    private final BenchmarkHistorySynchronizer synchronizer;

    public CdiBenchmarkService(
            BcbClient bcbClient,
            BenchmarkHistorySynchronizer synchronizer) {

        this.bcbClient = bcbClient;
        this.synchronizer = synchronizer;
    }

    public List<BenchmarkQuote> load(LocalDate startDate, LocalDate endDate) {
        return synchronizer.load(BenchmarkType.CDI, startDate, endDate, this::fetch);
    }

    private List<BenchmarkQuote> fetch(LocalDate startDate, LocalDate endDate) {
        List<BcbSeriesPointResponse> response =
                bcbClient.getSeries(
                        CDI_SERIES_CODE,
                        startDate,
                        endDate
                );

        var additions = new java.util.ArrayList<BenchmarkQuote>();

        for (BcbSeriesPointResponse point : response) {

            LocalDate date =
                    LocalDate.parse(
                            point.date(),
                            BCB_DATE_FORMATTER
                    );

            BigDecimal value =
                    new BigDecimal(point.value());

            if (!date.isBefore(startDate) && !date.isAfter(endDate)) {

                additions.add(
                        new BenchmarkQuote(
                                BenchmarkType.CDI,
                                date,
                                value
                        )
                );
            }
        }
        return additions;
    }
}
