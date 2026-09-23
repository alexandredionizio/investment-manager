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

    @Test
    void shouldReturnEmptyListWhenBcbReturnsNotFound() {

        server.expect(once(), requestTo(
                        "https://api.bcb.gov.br/dados/serie/bcdata.sgs.12/dados"
                                + "?formato=json"
                                + "&dataInicial=22/09/2026"
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
                                            "detail": "Value(s) not found"
                                          }
                                        }
                                        """)
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
}