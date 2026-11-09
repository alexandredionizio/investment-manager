package com.investmanager.api.benchmark.service;

import com.investmanager.api.benchmark.entity.BenchmarkQuote;
import com.investmanager.api.benchmark.model.BenchmarkType;
import com.investmanager.api.quote.client.BrapiClient;
import com.investmanager.api.quote.client.dto.BrapiIndexHistoricalPoint;
import com.investmanager.api.quote.client.dto.BrapiIndexHistoricalResponse;
import com.investmanager.api.quote.client.dto.BrapiIndexHistoricalResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
public class IbovBenchmarkService {

    private static final String IBOV_SYMBOL = "^BVSP";
    private static final String DAILY_INTERVAL = "1d";

    private static final ZoneId SAO_PAULO_ZONE =
            ZoneId.of("America/Sao_Paulo");

    private final BrapiClient brapiClient;
    private final BenchmarkHistorySynchronizer synchronizer;
    private final Clock clock;

    @Autowired
    public IbovBenchmarkService(
            BrapiClient brapiClient,
            BenchmarkHistorySynchronizer synchronizer) {

        this(
                brapiClient,
                synchronizer,
                Clock.system(SAO_PAULO_ZONE)
        );
    }

    IbovBenchmarkService(
            BrapiClient brapiClient,
            BenchmarkHistorySynchronizer synchronizer,
            Clock clock) {

        this.brapiClient = brapiClient;
        this.synchronizer = synchronizer;
        this.clock = clock;
    }

    public List<BenchmarkQuote> load(LocalDate startDate, LocalDate endDate) {
        // The index endpoint returns a range ending today. Reuse it across recovery windows.
        var responses = new java.util.HashMap<String, BrapiIndexHistoricalResponse>();
        return synchronizer.load(BenchmarkType.IBOV, startDate, endDate,
                (from, to) -> fetch(from, to, responses));
    }

    private List<BenchmarkQuote> fetch(LocalDate startDate, LocalDate endDate, java.util.Map<String, BrapiIndexHistoricalResponse> responses) {
        String range =
                determineRange(startDate);

        BrapiIndexHistoricalResponse response =
                responses.computeIfAbsent(range, ignored -> brapiClient.getIndexHistoricalQuotes(IBOV_SYMBOL, range, DAILY_INTERVAL));

        if (response == null || response.results() == null) {
            throw new IllegalStateException("Resposta inválida do provedor do IBOV");
        }
        if (response.results().isEmpty()) {
            return List.of();
        }

        BrapiIndexHistoricalResult result =
                response.results().getFirst();

        if (result == null || !IBOV_SYMBOL.equals(result.symbol()) || result.historicalDataPrice() == null) {
            throw new IllegalStateException("Resposta inválida do provedor do IBOV");
        }

        var additions = new java.util.ArrayList<BenchmarkQuote>();

        for (BrapiIndexHistoricalPoint price
                : result.historicalDataPrice()) {

            if (price == null || price.date() == null || price.close() == null) {
                throw new IllegalStateException("Ponto histórico inválido do provedor do IBOV");
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

                additions.add(
                        new BenchmarkQuote(
                                BenchmarkType.IBOV,
                                date,
                                value
                        )
                );
        }
        return additions;
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
