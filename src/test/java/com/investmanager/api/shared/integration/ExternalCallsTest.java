package com.investmanager.api.shared.integration;

import com.investmanager.api.quote.client.BrapiClient;
import com.investmanager.api.quote.exception.QuoteNotFoundException;
import com.investmanager.api.benchmark.client.bcb.BcbClient;
import com.investmanager.api.shared.config.RestClientConfig;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.*;
import org.springframework.http.MediaType;
import org.springframework.mock.web.*;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class ExternalCallsTest {
    static final String VALID = "{\"results\":[{\"symbol\":\"TEST11\",\"data\":{\"currency\":\"BRL\",\"regularMarketPrice\":10}}]}";
    IntegrationProperties properties;
    ExternalCalls calls;
    MockRestServiceServer server;
    BrapiClient client;
    @BeforeEach void setup() {
        properties = new IntegrationProperties();
        properties.getBrapi().setInitialBackoffMs(1);
        properties.getBrapi().setMaxBackoffMs(2);
        calls = new ExternalCalls(properties);
        var builder = RestClient.builder().baseUrl("https://brapi.test");
        server = MockRestServiceServer.bindTo(builder).build();
        client = new BrapiClient(builder.build(), calls);
    }
    @AfterEach void close() { calls.close(); }
    @Test void validResponse() {
        success();
        assertThat(client.getQuote("TEST11").results().getFirst().data().regularMarketPrice()).isEqualByComparingTo("10");
        server.verify();
    }
    @Test void timeoutThenSuccess() {
        server.expect(anything()).andRespond(withException(new SocketTimeoutException("timeout")));
        success();
        assertThat(client.getQuote("TEST11")).isNotNull(); server.verify();
    }
    @Test void timeoutExhaustionIsExplicit() {
        for (int i=0;i<3;i++) server.expect(anything()).andRespond(withException(new SocketTimeoutException()));
        assertThatThrownBy(() -> client.getQuote("TEST11")).isInstanceOfSatisfying(ExternalIntegrationException.class,
                e -> assertThat(e.status()).isEqualTo(504));
        server.verify();
    }
    @ParameterizedTest @ValueSource(ints={429,502,503,504})
    void transientStatusThenSuccess(int status) {
        server.expect(anything()).andRespond(withStatus(HttpStatus.valueOf(status)).header("Retry-After", "0"));
        success(); assertThat(client.getQuote("TEST11")).isNotNull(); server.verify();
    }
    @ParameterizedTest @ValueSource(ints={400,401,403,500})
    void permanentStatusIsNotRetriedOrExposedAsUserAuthentication(int status) {
        server.expect(anything()).andRespond(withStatus(HttpStatus.valueOf(status)).body("secret-provider-body"));
        assertThatThrownBy(() -> client.getQuote("TEST11")).isInstanceOfSatisfying(ExternalIntegrationException.class,
                e -> { assertThat(e.status()).isEqualTo(502); assertThat(e.getMessage()).doesNotContain("secret"); });
        server.verify();
    }
    @Test void notFoundIsNotRetried() {
        server.expect(anything()).andRespond(withStatus(HttpStatus.NOT_FOUND));
        assertThatThrownBy(() -> client.getQuote("TEST11")).isInstanceOf(QuoteNotFoundException.class); server.verify();
    }
    @Test void emptyResultsRemainMissingData() {
        server.expect(anything()).andRespond(withSuccess("{\"results\":[]}",MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> client.getQuote("TEST11")).isInstanceOf(QuoteNotFoundException.class); server.verify();
    }
    @ParameterizedTest @ValueSource(strings={"", "null", "{}", "{", "{\"results\":[null]}",
            "{\"results\":[{\"data\":{\"currency\":\"USD\",\"regularMarketPrice\":10}}]}"})
    void invalidPayloadIsNotRetried(String body) {
        server.expect(anything()).andRespond(withSuccess(body,MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> client.getQuote("TEST11")).isInstanceOfSatisfying(ExternalIntegrationException.class,
                e -> assertThat(e.category()).isEqualTo("invalid_payload")); server.verify();
    }
    @Test void retryAfterBeyondBudgetDoesNotCauseEarlyRetry() {
        properties.getBrapi().setOperationTimeoutMs(200);
        server.expect(anything()).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS).header("Retry-After","60"));
        assertThatThrownBy(() -> client.getQuote("TEST11")).isInstanceOf(ExternalIntegrationException.class);
        server.verify();
    }
    @Test void retryAfterSupportsSecondsDatesAndOversizedValues() {
        Instant now=Instant.parse("2026-11-06T00:00:00Z");
        assertThat(ExternalCalls.retryAfterMillis(http("2"), now)).isEqualTo(2000);
        assertThat(ExternalCalls.retryAfterMillis(http(DateTimeFormatter.RFC_1123_DATE_TIME.format(now.plusSeconds(3).atZone(ZoneOffset.UTC))),now)).isEqualTo(3000);
        assertThat(ExternalCalls.retryAfterMillis(http("999999999999999999999"),now)).isGreaterThan(60000);
        assertThat(ExternalCalls.retryAfterMillis(http("invalid"),now)).isZero();
    }
    @Test void retryAfterActuallyDelaysRetry() {
        server.expect(anything()).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS).header("Retry-After","1"));
        success(); long start=System.nanoTime(); client.getQuote("TEST11");
        assertThat(Duration.ofNanos(System.nanoTime()-start)).isGreaterThanOrEqualTo(Duration.ofSeconds(1)); server.verify();
    }
    @Test void networkFailureClassificationIsSelective() {
        assertThat(ExternalCalls.transientFailure(new ResourceAccessException("",new ConnectException()))).isTrue();
        assertThat(ExternalCalls.transientFailure(new ResourceAccessException("",new UnknownHostException()))).isFalse();
        assertThat(ExternalCalls.transientFailure(new ResourceAccessException("",new javax.net.ssl.SSLException("")))).isFalse();
        assertThat(ExternalCalls.transientFailure(new ResourceAccessException("unknown"))).isFalse();
    }
    @Test void operationDeadlineCancelsWork() throws Exception {
        properties.getBrapi().setOperationTimeoutMs(100);
        CountDownLatch interrupted=new CountDownLatch(1);
        assertThatThrownBy(() -> calls.get("brapi","test",() -> {
            try { Thread.sleep(5000); } catch (InterruptedException e) { interrupted.countDown(); Thread.currentThread().interrupt(); }
            return "late";
        })).isInstanceOfSatisfying(ExternalIntegrationException.class,e -> assertThat(e.status()).isEqualTo(504));
        assertThat(interrupted.await(1,TimeUnit.SECONDS)).isTrue();
    }
    @Test void requestBudgetIsSharedAndRemovedAfterRequest() throws Exception {
        properties.setRequestBudgetMs(1);
        var filter=new IntegrationBudgetFilter(properties);
        AtomicInteger invocations=new AtomicInteger();
        filter.doFilter(new MockHttpServletRequest(),new MockHttpServletResponse(),(request,response) -> {
            try { Thread.sleep(10); } catch (InterruptedException e) { throw new RuntimeException(e); }
            assertThatThrownBy(() -> calls.get("brapi","test",invocations::incrementAndGet)).isInstanceOf(ExternalIntegrationException.class);
        });
        assertThat(invocations).hasValue(0);
        assertThat(calls.get("brapi","test",invocations::incrementAndGet)).isEqualTo(1);
    }
    @Test void bcbUsesSameRetryPolicyAndPreservesEmptySeries() {
        var builder=RestClient.builder().baseUrl("https://bcb.test");
        var bcbServer=MockRestServiceServer.bindTo(builder).build();
        var bcb=new BcbClient(builder.build(),calls);
        properties.getBcb().setInitialBackoffMs(1);
        bcbServer.expect(anything()).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        bcbServer.expect(anything()).andRespond(withSuccess("[]",MediaType.APPLICATION_JSON));
        assertThat(bcb.getSeries(12,LocalDate.of(2026,9,1),LocalDate.of(2026,9,2))).isEmpty(); bcbServer.verify();
    }
    @Test void clientsHaveIsolatedCredentialsAndBoundFullResponseBody() throws Exception {
        var local=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        var executor=Executors.newVirtualThreadPerTaskExecutor(); local.setExecutor(executor);
        AtomicReference<String> bcbAuth=new AtomicReference<>(); AtomicReference<String> brapiAuth=new AtomicReference<>();
        local.createContext("/dados/",exchange -> {
            bcbAuth.set(exchange.getRequestHeaders().getFirst("Authorization"));
            exchange.getResponseHeaders().set("Content-Type","application/json");
            byte[] data="[]".getBytes(StandardCharsets.UTF_8); exchange.sendResponseHeaders(200,data.length);
            exchange.getResponseBody().write(data); exchange.close();
        });
        local.createContext("/api/",exchange -> {
            brapiAuth.set(exchange.getRequestHeaders().getFirst("Authorization"));
            exchange.getResponseHeaders().set("Content-Type","application/json");
            exchange.sendResponseHeaders(200,1000); exchange.getResponseBody().write('{'); exchange.getResponseBody().flush();
            try { Thread.sleep(1500); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            exchange.close();
        });
        local.start();
        try {
            String url="http://127.0.0.1:"+local.getAddress().getPort();
            var config=new RestClientConfig();
            var brapi=new BrapiClient(config.brapiRestClient(properties,"test-token",url),calls);
            var bcb=new BcbClient(config.bcbRestClient(properties,url),calls);
            assertThat(bcb.getSeries(12,LocalDate.of(2026,9,1),LocalDate.of(2026,9,2))).isEmpty();
            properties.getBrapi().setOperationTimeoutMs(250);
            long start=System.nanoTime();
            assertThatThrownBy(() -> brapi.getQuote("TEST11")).isInstanceOfSatisfying(ExternalIntegrationException.class,e -> assertThat(e.status()).isEqualTo(504));
            assertThat(Duration.ofNanos(System.nanoTime()-start)).isLessThan(Duration.ofSeconds(1));
            assertThat(bcbAuth.get()).isNull(); assertThat(brapiAuth.get()).isEqualTo("Bearer test-token");
        } finally { local.stop(0); executor.shutdownNow(); }
    }
    private void success() { server.expect(anything()).andRespond(withSuccess(VALID,MediaType.APPLICATION_JSON)); }
    private RestClientResponseException http(String retryAfter) {
        var headers=new HttpHeaders(); headers.set("Retry-After",retryAfter);
        return HttpClientErrorException.create(HttpStatus.TOO_MANY_REQUESTS,"limited",headers,new byte[0],StandardCharsets.UTF_8);
    }
}
