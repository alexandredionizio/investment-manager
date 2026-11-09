package com.investmanager.api.benchmark.client.bcb;

import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import com.investmanager.api.shared.integration.ExternalCalls;
import com.investmanager.api.shared.integration.IntegrationProperties;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;

@Component
public class BcbClient {

    private static final String BASE_URL =
            "https://api.bcb.gov.br";

    private static final DateTimeFormatter QUERY_DATE_FORMATTER =
            DateTimeFormatter.ofPattern("dd/MM/uuuu").withResolverStyle(java.time.format.ResolverStyle.STRICT);

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    private final ExternalCalls calls;

    public BcbClient(RestClient.Builder builder) {
        this(builder.clone().baseUrl(BASE_URL).build(), new ExternalCalls(new IntegrationProperties()));
    }

    @Autowired
    public BcbClient(@Qualifier("bcbRestClient") RestClient client, ExternalCalls calls) {
        this.restClient = client;
        this.calls = calls;
        this.objectMapper = new ObjectMapper();
    }

    public List<BcbSeriesPointResponse> getSeries(
            int seriesCode,
            LocalDate startDate,
            LocalDate endDate) {

        return calls.get("bcb", "cdi", () -> fetchSeries(seriesCode, startDate, endDate));
    }

    private List<BcbSeriesPointResponse> fetchSeries(int seriesCode, LocalDate startDate, LocalDate endDate) {
        // SGS rejects equal start/end dates. Widen only the provider query,
        // then return exclusively the originally requested interval.
        LocalDate queryStartDate = startDate.equals(endDate) ? startDate.minusDays(1) : startDate;

        try {

            JsonNode response = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/dados/serie/bcdata.sgs.{seriesCode}/dados")
                            .queryParam("formato", "json")
                            .queryParam(
                                    "dataInicial",
                                    queryStartDate.format(
                                            QUERY_DATE_FORMATTER
                                    )
                            )
                            .queryParam(
                                    "dataFinal",
                                    endDate.format(
                                            QUERY_DATE_FORMATTER
                                    )
                            )
                            .build(seriesCode)
                    )
                    .retrieve()
                    .body(JsonNode.class);

            // SGS can report the same no-values envelope with HTTP 200 or 404.
            if (isNoValuesResponse(response)) {
                return List.of();
            }
            if (response == null || !response.isArray()) {
                throw new IllegalStateException("Resposta inválida do provedor do CDI");
            }

            BcbSeriesPointResponse[] points =
                    objectMapper.treeToValue(
                            response,
                            BcbSeriesPointResponse[].class
                    );

            if (points == null || Arrays.stream(points)
                    .anyMatch(point -> point == null || point.date() == null || point.value() == null)) {
                throw new IllegalStateException("Resposta inválida do provedor do CDI");
            }

            // Validate every point before any caller can persist a partial response.
            for (var point : points) {
                LocalDate.parse(point.date(), QUERY_DATE_FORMATTER);
                new java.math.BigDecimal(point.value());
            }
            return Arrays.stream(points)
                    .filter(point -> {
                        LocalDate date = LocalDate.parse(point.date(), QUERY_DATE_FORMATTER);
                        return !date.isBefore(startDate) && !date.isAfter(endDate);
                    })
                    .toList();

        } catch (HttpClientErrorException.NotFound exception) {
            if (isNoValuesResponse(objectMapper.readTree(exception.getResponseBodyAsString()))) {
                return List.of();
            }
            throw exception;
        }
    }

    private boolean isNoValuesResponse(JsonNode response) {
        if (response == null) {
            return false;
        }
        JsonNode error = response.path("erro");
        String detail = error.path("detail").asString();
        return error.path("statusCode").asInt() == 404
                && ("Value(s) not found".equals(detail)
                || "br.gov.bcb.pec.sgs.comum.excecoes.SGSNegocioException: Value(s) not found".equals(detail));
    }
}