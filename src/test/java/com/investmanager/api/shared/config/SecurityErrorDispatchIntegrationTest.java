package com.investmanager.api.shared.config;

import com.investmanager.api.benchmark.service.BenchmarkComparisonService;
import com.investmanager.api.benchmark.exception.BenchmarkHistoryIncompleteException;
import com.investmanager.api.benchmark.model.BenchmarkType;
import com.investmanager.api.portfoliohistory.model.PortfolioHistoryPeriod;
import com.investmanager.api.quote.exception.QuoteNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

// Real HTTP/Tomcat dispatch: standalone MockMvc does not reproduce the /error redispatch.
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "brapi.token=test-token",
        "jwt.secret=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY="
})
@Testcontainers
class SecurityErrorDispatchIntegrationTest {
    @Container @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

    @LocalServerPort int port;
    @Autowired ObjectMapper mapper;
    @MockitoBean BenchmarkComparisonService comparison;
    private final HttpClient client = HttpClient.newHttpClient();
    private String token;

    @BeforeEach
    void loginThroughRealEndpoint() throws Exception {
        String credentials = "\"email\":\"dispatch-" + UUID.randomUUID() + "@example.test\",\"password\":\"test-password-123\"";
        var created = post("/api/v1/users", "{\"name\":\"Dispatch test\"," + credentials + "}");
        assertThat(created.statusCode()).isEqualTo(201);
        var login = post("/api/v1/auth/login", "{" + credentials + "}");
        assertThat(login.statusCode()).isEqualTo(200);
        token = mapper.readTree(login.body()).path("token").asString();
        assertThat(token).isNotBlank();
    }

    static Stream<RuntimeException> technicalFailures() {
        return Stream.of(new HttpClientErrorException(HttpStatus.BAD_REQUEST, "BCB: invalid date range"),
                new ResourceAccessException("provider timeout"), new IllegalStateException("invalid provider payload"));
    }

    @ParameterizedTest
    @MethodSource("technicalFailures")
    void technicalFailureIs500AndSameJwtStillWorks(RuntimeException failure) throws Exception {
        when(comparison.getComparison(eq(1L), anyLong(), eq(PortfolioHistoryPeriod.ONE_MONTH), isNull(), isNull(), any()))
                .thenThrow(failure);
        var response = get("/api/v1/portfolios/1/benchmark-comparison?period=ONE_MONTH", token);
        assertThat(response.statusCode()).isEqualTo(500);
        assertThat(mapper.readTree(response.body()).path("status").asInt()).isEqualTo(500);
        assertThat(response.body()).doesNotContain("Não autenticado");
        doReturn(List.of()).when(comparison)
                .getComparison(eq(1L), anyLong(), eq(PortfolioHistoryPeriod.ONE_MONTH), isNull(), isNull(), any());
        assertThat(get("/api/v1/portfolios/1/benchmark-comparison?period=ONE_MONTH", token).statusCode()).isEqualTo(200);
    }

    @Test
    void missingTokenStillReturns401() throws Exception {
        assertThat(get("/api/v1/portfolios/1/benchmark-comparison?period=ONE_MONTH", null).statusCode()).isEqualTo(401);
        verifyNoInteractions(comparison);
    }

    @ParameterizedTest
    @EnumSource(BenchmarkType.class)
    void incompleteBenchmarkHistoryReturns503WithoutInvalidatingSession(BenchmarkType benchmark) throws Exception {
        var missingDate = LocalDate.of(2026, 9, 2);
        when(comparison.getComparison(eq(1L), anyLong(), eq(PortfolioHistoryPeriod.ONE_MONTH), isNull(), isNull(), any()))
                .thenThrow(new BenchmarkHistoryIncompleteException(benchmark, List.of(missingDate)));

        var response = get("/api/v1/portfolios/1/benchmark-comparison?period=ONE_MONTH", token);
        assertThat(response.statusCode()).isEqualTo(503);
        var body = mapper.readTree(response.body());
        assertThat(body.path("status").asInt()).isEqualTo(503);
        assertThat(body.path("error").asString()).isEqualTo("Histórico de benchmark incompleto");
        assertThat(body.path("message").asString()).contains(benchmark.name(), missingDate.toString());
        assertThat(response.body()).doesNotContain("portfolioReturn", "cdiReturn", "ibovReturn");

        doReturn(List.of()).when(comparison)
                .getComparison(eq(1L), anyLong(), eq(PortfolioHistoryPeriod.ONE_MONTH), isNull(), isNull(), any());
        assertThat(get("/api/v1/portfolios/1/benchmark-comparison?period=ONE_MONTH", token).statusCode()).isEqualTo(200);
    }

    @Test
    void invalidTokenStillReturns401() throws Exception {
        assertThat(get("/api/v1/portfolios/1/benchmark-comparison?period=ONE_MONTH", "invalid-token").statusCode()).isEqualTo(401);
        verifyNoInteractions(comparison);
    }

    @Test
    void directErrorRequestIsNotMadePublic() throws Exception {
        assertThat(get("/error", null).statusCode()).isEqualTo(401);
    }

    @Test
    void missingQuoteRemainsExplicit404() throws Exception {
        when(comparison.getComparison(eq(1L), anyLong(), eq(PortfolioHistoryPeriod.ONE_MONTH), isNull(), isNull(), any()))
                .thenThrow(new QuoteNotFoundException("ITUB4"));
        var response = get("/api/v1/portfolios/1/benchmark-comparison?period=ONE_MONTH", token);
        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(mapper.readTree(response.body()).path("error").asString()).isEqualTo("Cotação indisponível");
    }

    private HttpResponse<String> get(String path, String jwt) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path));
        if (jwt != null) request.header("Authorization", "Bearer " + jwt);
        return client.send(request.GET().build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> post(String path, String json) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(json)).build(),
                HttpResponse.BodyHandlers.ofString());
    }
}
