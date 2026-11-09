package com.investmanager.api.benchmark.client.bcb;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class BcbClientTest {

    private MockRestServiceServer server;
    private BcbClient bcbClient;

    @BeforeEach
    void setUp() {

        RestClient.Builder builder = RestClient.builder();

        server = MockRestServiceServer
                .bindTo(builder)
                .build();

        bcbClient = new BcbClient(builder);
    }

    @Test
    void shouldGetSeriesFromBcb() {

        String responseBody = """
                [
                  {
                    "data": "21/09/2026",
                    "valor": "0.055000"
                  },
                  {
                    "data": "22/09/2026",
                    "valor": "0.055100"
                  }
                ]
                """;

        server.expect(once(), requestTo(
                        "https://api.bcb.gov.br/dados/serie/bcdata.sgs.12/dados"
                                + "?formato=json"
                                + "&dataInicial=21/09/2026"
                                + "&dataFinal=22/09/2026"
                ))
                .andExpect(method(GET))
                .andRespond(withSuccess(
                        responseBody,
                        APPLICATION_JSON
                ));

        List<BcbSeriesPointResponse> result =
                bcbClient.getSeries(
                        12,
                        LocalDate.of(2026, 9, 21),
                        LocalDate.of(2026, 9, 22)
                );

        assertNotNull(result);
        assertEquals(2, result.size());

        assertEquals(
                "21/09/2026",
                result.get(0).date()
        );

        assertEquals(
                "0.055000",
                result.get(0).value()
        );

        assertEquals(
                "22/09/2026",
                result.get(1).date()
        );

        assertEquals(
                "0.055100",
                result.get(1).value()
        );

        server.verify();
    }

    @Test
    void shouldReturnEmptyListWhenBcbReturnsEmptyArray() {

        server.expect(once(), requestTo(
                        "https://api.bcb.gov.br/dados/serie/bcdata.sgs.12/dados"
                                + "?formato=json"
                                + "&dataInicial=21/09/2026"
                                + "&dataFinal=22/09/2026"
                ))
                .andExpect(method(GET))
                .andRespond(withSuccess(
                        "[]",
                        APPLICATION_JSON
                ));

        List<BcbSeriesPointResponse> result =
                bcbClient.getSeries(
                        12,
                        LocalDate.of(2026, 9, 21),
                        LocalDate.of(2026, 9, 22)
                );

        assertNotNull(result);
        assertEquals(0, result.size());

        server.verify();
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {
            "Value(s) not found",
            "br.gov.bcb.pec.sgs.comum.excecoes.SGSNegocioException: Value(s) not found"
    })
    void shouldReturnEmptyListWhenBcbReturnsNotFound(String detail) {

        server.expect(once(), requestTo(
                        "https://api.bcb.gov.br/dados/serie/bcdata.sgs.12/dados"
                                + "?formato=json"
                                + "&dataInicial=21/09/2026"
                                + "&dataFinal=22/09/2026"
                ))
                .andExpect(method(GET))
                .andRespond(
                        withStatus(HttpStatus.NOT_FOUND)
                                .contentType(APPLICATION_JSON)
                                .body("""
                                        {
                                          "erro": {
                                            "statusCode": 404,
                                            "detail": "%s"
                                          }
                                        }
                                        """.formatted(detail))
                );

        List<BcbSeriesPointResponse> result =
                bcbClient.getSeries(
                        12,
                        LocalDate.of(2026, 9, 22),
                        LocalDate.of(2026, 9, 22)
                );

        assertNotNull(result);
        assertEquals(0, result.size());

        server.verify();
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {
            "Value(s) not found",
            "br.gov.bcb.pec.sgs.comum.excecoes.SGSNegocioException: Value(s) not found"
    })
    void shouldReturnEmptyListWhenBcbReportsNoValuesInSuccessfulResponse(String detail) {
        server.expect(org.springframework.test.web.client.match.MockRestRequestMatchers.anything())
                .andRespond(withSuccess("""
                        {"erro":{"statusCode":404,"detail":"%s"}}
                        """.formatted(detail), APPLICATION_JSON));
        assertEquals(List.of(), bcbClient.getSeries(12, LocalDate.of(2026, 10, 30), LocalDate.of(2026, 10, 31)));
        server.verify();
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"null", "{}", "[{}]", "[null]",
            "{\"erro\":{\"statusCode\":404,\"detail\":\"Resource not found\"}}",
            "{\"erro\":{\"statusCode\":500,\"detail\":\"Value(s) not found\"}}",
            "{\"erro\":{\"statusCode\":404,\"detail\":\"other.Exception: Value(s) not found\"}}"})
    void shouldRejectInvalidSuccessfulResponse(String response) {
        server.expect(org.springframework.test.web.client.match.MockRestRequestMatchers.anything())
                .andRespond(withSuccess(response, APPLICATION_JSON));
        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class,
                () -> bcbClient.getSeries(12, LocalDate.of(2026, 9, 21), LocalDate.of(2026, 9, 22)));
        server.verify();
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(ints = {400, 401, 403, 404, 500})
    void shouldNotTreatArbitraryHttpErrorAsMissingBenchmark(int status) {
        server.expect(org.springframework.test.web.client.match.MockRestRequestMatchers.anything())
                .andRespond(withStatus(HttpStatus.valueOf(status)).contentType(APPLICATION_JSON).body("{}"));
        org.junit.jupiter.api.Assertions.assertThrows(com.investmanager.api.shared.integration.ExternalIntegrationException.class,
                () -> bcbClient.getSeries(12, LocalDate.of(2026, 9, 21), LocalDate.of(2026, 9, 22)));
        server.verify();
    }


    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {
            "Resource not found",
            "other.Exception: Value(s) not found",
            "br.gov.bcb.pec.sgs.comum.excecoes.SGSNegocioException: Database unavailable"
    })
    void shouldNotTreatOtherNotFoundDetailsAsMissingValues(String detail) {
        server.expect(org.springframework.test.web.client.match.MockRestRequestMatchers.anything())
                .andRespond(withStatus(HttpStatus.NOT_FOUND).contentType(APPLICATION_JSON)
                        .body("""
                                {"erro":{"statusCode":404,"detail":"%s"}}
                                """.formatted(detail)));
        org.junit.jupiter.api.Assertions.assertThrows(com.investmanager.api.shared.integration.ExternalIntegrationException.class,
                () -> bcbClient.getSeries(12, LocalDate.of(2026, 10, 30), LocalDate.of(2026, 10, 31)));
        server.verify();
    }

    @Test
    void shouldNotTreatDifferentProviderErrorCodeAsMissingValues() {
        server.expect(org.springframework.test.web.client.match.MockRestRequestMatchers.anything())
                .andRespond(withStatus(HttpStatus.NOT_FOUND).contentType(APPLICATION_JSON)
                        .body("""
                                {"erro":{"statusCode":500,"detail":"br.gov.bcb.pec.sgs.comum.excecoes.SGSNegocioException: Value(s) not found"}}
                                """));
        org.junit.jupiter.api.Assertions.assertThrows(com.investmanager.api.shared.integration.ExternalIntegrationException.class,
                () -> bcbClient.getSeries(12, LocalDate.of(2026, 10, 30), LocalDate.of(2026, 10, 31)));
        server.verify();
    }

    @Test
    void shouldWidenSingleDayQueryAndReturnOnlyRequestedDay() {
        server.expect(requestTo("https://api.bcb.gov.br/dados/serie/bcdata.sgs.12/dados"
                        + "?formato=json&dataInicial=29/10/2026&dataFinal=30/10/2026"))
                .andRespond(withSuccess("""
                        [{"data":"29/10/2026","valor":"0.05"},{"data":"30/10/2026","valor":"0.06"}]
                        """, APPLICATION_JSON));
        var result = bcbClient.getSeries(12, LocalDate.of(2026, 10, 30), LocalDate.of(2026, 10, 30));
        assertEquals(1, result.size());
        assertEquals("30/10/2026", result.getFirst().date());
        assertEquals("0.06", result.getFirst().value());
        server.verify();
    }

    @Test
    void shouldNotSubstitutePreviousDayForUnpublishedRequestedDay() {
        server.expect(requestTo("https://api.bcb.gov.br/dados/serie/bcdata.sgs.12/dados"
                        + "?formato=json&dataInicial=29/10/2026&dataFinal=30/10/2026"))
                .andRespond(withSuccess("""
                        [{"data":"29/10/2026","valor":"0.05"}]
                        """, APPLICATION_JSON));
        assertEquals(List.of(), bcbClient.getSeries(12, LocalDate.of(2026, 10, 30), LocalDate.of(2026, 10, 30)));
        server.verify();
    }


    @Test
    void shouldRejectImpossibleCalendarDateInsteadOfNormalizingIt() {
        server.expect(org.springframework.test.web.client.match.MockRestRequestMatchers.anything())
                .andRespond(withSuccess("""
                        [{"data":"31/02/2026","valor":"0.05"}]
                        """, APPLICATION_JSON));
        org.junit.jupiter.api.Assertions.assertThrows(com.investmanager.api.shared.integration.ExternalIntegrationException.class,
                () -> bcbClient.getSeries(12, LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 28)));
        server.verify();
    }
}
