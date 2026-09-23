package com.investmanager.api.benchmark.client.bcb;

import org.springframework.stereotype.Component;
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
            DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public BcbClient(RestClient.Builder restClientBuilder) {

        this.restClient = restClientBuilder
                .baseUrl(BASE_URL)
                .build();

        this.objectMapper = new ObjectMapper();
    }

    public List<BcbSeriesPointResponse> getSeries(
            int seriesCode,
            LocalDate startDate,
            LocalDate endDate) {

        try {

            JsonNode response = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/dados/serie/bcdata.sgs.{seriesCode}/dados")
                            .queryParam("formato", "json")
                            .queryParam(
                                    "dataInicial",
                                    startDate.format(
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

            if (response == null || !response.isArray()) {
                return List.of();
            }

            BcbSeriesPointResponse[] points =
                    objectMapper.treeToValue(
                            response,
                            BcbSeriesPointResponse[].class
                    );

            if (points == null) {
                return List.of();
            }

            return Arrays.asList(points);

        } catch (HttpClientErrorException.NotFound exception) {

            return List.of();
        }
    }
}