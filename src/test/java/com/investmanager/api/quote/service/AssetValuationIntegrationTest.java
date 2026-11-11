package com.investmanager.api.quote.service;

import com.investmanager.api.asset.*;
import com.investmanager.api.asset.repository.AssetRepository;
import com.investmanager.api.auth.security.JwtService;
import com.investmanager.api.benchmark.service.*;
import com.investmanager.api.portfolio.Portfolio;
import com.investmanager.api.portfolio.repository.PortfolioRepository;
import com.investmanager.api.quote.client.BrapiClient;
import com.investmanager.api.quote.client.dto.*;
import com.investmanager.api.quote.exception.QuoteNotFoundException;
import com.investmanager.api.transaction.*;
import com.investmanager.api.transaction.repository.TransactionRepository;
import com.investmanager.api.user.entity.User;
import com.investmanager.api.user.repository.UserRepository;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.core.env.Environment;
import org.springframework.data.redis.core.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.client.ResourceAccessException;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.*;
import java.time.*;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "brapi.token=test-token", "jwt.secret=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY="})
@Testcontainers
class AssetValuationIntegrationTest {
    @Container @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");
    @Autowired AssetRepository assets;
    @Autowired TransactionRepository transactions;
    @Autowired PortfolioRepository portfolios;
    @Autowired UserRepository users;
    @Autowired JwtService jwt;
    @Autowired JdbcTemplate jdbc;
    @Autowired Environment environment;
    @MockitoBean BrapiClient provider;
    @MockitoBean StringRedisTemplate redis;
    @MockitoBean CdiBenchmarkService cdi;
    @MockitoBean IbovBenchmarkService ibov;
    private ValueOperations<String,String> cache;
    private Portfolio portfolio;
    private String token;
    private final LocalDate first = LocalDate.of(2026,9,1);
    private final JsonMapper json = JsonMapper.builder().build();
    private static final String PERIOD = "?period=CUSTOM&startDate=2026-09-01&endDate=2026-09-02";

    @BeforeEach void setup() {
        // Only the isolated Testcontainers database; never the user's database.
        jdbc.execute("TRUNCATE transactions, incomes, portfolio_asset_locks, portfolios, assets, brokers, users RESTART IDENTITY CASCADE");
        User user = users.save(new User("Valuation test", "valuation@test.local", "test"));
        portfolio = portfolios.save(new Portfolio("Valuation", "Test", user));
        token = jwt.generateToken(user);
        cache = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(cache);
        when(provider.getQuote(anyString())).thenAnswer(inv -> quote(inv.getArgument(0), "BRL", "10"));
        when(provider.getHistoricalQuotes(anyString(), any(LocalDate.class), any(LocalDate.class), eq("1d")))
                .thenAnswer(inv -> historical(inv.getArgument(0)));
    }

    @ParameterizedTest @EnumSource(AssetType.class)
    void classControlsValuationButNotTransactionAccounting(AssetType type) throws Exception {
        Asset asset = asset("TEST11", type, "B3");
        buy(asset, first);
        JsonNode accounting = get(base()+"/positions", 200);
        assertThat(accounting.get(0).get("quantity").decimalValue()).isEqualByComparingTo("10");
        assertThat(accounting.get(0).get("totalCost").decimalValue()).isEqualByComparingTo("100");
        boolean supported = type == AssetType.STOCK || type == AssetType.FII || type == AssetType.ETF;
        if (supported) {
            assertThat(get("/api/v1/quotes/test11",200).get("price").decimalValue()).isEqualByComparingTo("10");
            JsonNode market = get(base()+"/positions/market",200);
            assertThat(market.get(0).get("currentValue").decimalValue()).isEqualByComparingTo("100");
            assertThat(market.get(0).get("profitLoss").decimalValue()).isEqualByComparingTo("0");
            assertThat(get(base()+"/history"+PERIOD,200).get("points").size()).isEqualTo(2);
            assertThat(get(base()+"/returns"+PERIOD,200).get("totalReturn").decimalValue()).isEqualByComparingTo("0");
            assertThat(get(base()+"/benchmark-comparison"+PERIOD,200).size()).isGreaterThan(0);
        } else {
            for (String path : List.of("/api/v1/quotes/TEST11", base()+"/positions/market",
                    base()+"/history"+PERIOD, base()+"/returns"+PERIOD, base()+"/benchmark-comparison"+PERIOD)) {
                JsonNode error = get(path,404);
                assertThat(error.get("error").asText()).isEqualTo("Cotação não suportada");
                assertThat(error.get("message").asText()).contains(type.name());
            }
            verifyNoInteractions(provider, cache, cdi, ibov);
        }
    }

    @Test void mixedPortfolioNeverReturnsPartialValuationAsWholePortfolio() throws Exception {
        buy(asset("TEST11", AssetType.STOCK, "B3"),first);
        buy(asset("BTC", AssetType.CRYPTO, "BINANCE"),first);
        for (String suffix : List.of("/positions/market", "/history"+PERIOD, "/returns"+PERIOD, "/benchmark-comparison"+PERIOD)) {
            assertThat(get(base()+suffix,404).get("message").asText()).contains("BTC");
        }
        verify(provider,never()).getQuote("BTC");
        verify(provider,never()).getHistoricalQuotes(eq("BTC"),any(LocalDate.class),any(LocalDate.class),anyString());
    }

    @ParameterizedTest @NullAndEmptySource @ValueSource(strings={"NASDAQ"})
    void missingOrForeignExchangeRemainsUnavailable(String exchange) throws Exception {
        buy(asset("TEST11",AssetType.STOCK,exchange),first);
        assertThat(get(base()+"/positions/market",404).get("message").asText()).contains("B3");
        verifyNoInteractions(provider,cache);
    }

    @Test void unsupportedAssetOutsideRequestedPeriodDoesNotInvalidateSupportedHistory() throws Exception {
        buy(asset("TEST11",AssetType.STOCK,"B3"),first);
        Asset crypto = asset("BTC",AssetType.CRYPTO,"BINANCE");
        buy(crypto,first.minusDays(3));
        transactions.save(new Transaction(portfolio,crypto,TransactionType.SELL,BigDecimal.TEN,BigDecimal.TEN,first.minusDays(2)));
        buy(crypto,first.plusDays(10));
        assertThat(get(base()+"/history"+PERIOD,200).get("points").size()).isEqualTo(2);
        assertThat(get(base()+"/returns"+PERIOD,200).get("totalReturn").decimalValue()).isEqualByComparingTo("0");
        verify(provider,never()).getHistoricalQuotes(eq("BTC"),any(LocalDate.class),any(LocalDate.class),anyString());
    }

    @Test void missingSupportedQuoteIs404ButTechnicalFailureRemains500() throws Exception {
        buy(asset("TEST11",AssetType.STOCK,"B3"),first);
        doThrow(new QuoteNotFoundException("TEST11")).when(provider).getQuote("TEST11");
        assertThat(get(base()+"/positions/market",404).get("error").asText()).isEqualTo("Cotação indisponível");
        doThrow(new ResourceAccessException("provider timeout")).when(provider).getQuote("TEST11");
        get(base()+"/positions/market",500);
        doReturn(quote("TEST11","USD","10")).when(provider).getQuote("TEST11");
        get(base()+"/positions/market",500);
        when(provider.getHistoricalQuotes(eq("TEST11"),any(LocalDate.class),any(LocalDate.class),eq("1d")))
                .thenThrow(new ResourceAccessException("history timeout"));
        get(base()+"/returns"+PERIOD,500);
    }

    @ParameterizedTest @ValueSource(ints={502,503,504})
    void externalOutagePreservesAccountingAndExposesSafeError(int status) throws Exception {
        buy(asset("TEST11",AssetType.STOCK,"B3"),first);
        long before=transactions.count();
        doThrow(new com.investmanager.api.shared.integration.ExternalIntegrationException(status,"test"))
                .when(provider).getQuote("TEST11");
        JsonNode failure=get(base()+"/positions/market",status);
        assertThat(failure.get("error").asText()).isEqualTo("Falha na integração externa");
        JsonNode accounting=get(base()+"/positions",200);
        assertThat(accounting.get(0).get("quantity").decimalValue()).isEqualByComparingTo("10");
        assertThat(accounting.get(0).get("totalCost").decimalValue()).isEqualByComparingTo("100");
        assertThat(transactions.count()).isEqualTo(before);
    }

    @Test void realZeroMarketPriceIsNotMissingData() throws Exception {
        buy(asset("TEST11",AssetType.ETF,"B3"),first);
        when(provider.getQuote("TEST11")).thenReturn(quote("TEST11","BRL","0"));
        JsonNode result=get(base()+"/positions/market",200).get(0);
        assertThat(result.get("currentPrice").decimalValue()).isEqualByComparingTo("0");
        assertThat(result.get("currentValue").decimalValue()).isEqualByComparingTo("0");
        assertThat(result.get("profitLoss").decimalValue()).isEqualByComparingTo("-100");
    }

    @Test void emptyPortfolioRemainsEmptyWithoutProviderCalls() throws Exception {
        assertThat(get(base()+"/positions/market",200).size()).isZero();
        assertThat(get(base()+"/history"+PERIOD,200).get("points").size()).isZero();
        assertThat(get(base()+"/returns"+PERIOD,200).get("totalReturn").isNull()).isTrue();
        verifyNoInteractions(provider,cache);
    }

    private String base() { return "/api/v1/portfolios/"+portfolio.getId(); }
    private Asset asset(String ticker,AssetType type,String exchange) {
        Asset a=new Asset();a.setTicker(ticker);a.setName(ticker);a.setType(type);a.setExchange(exchange);
        return assets.save(a);
    }
    private void buy(Asset asset,LocalDate date) {
        transactions.save(new Transaction(portfolio,asset,TransactionType.BUY,BigDecimal.TEN,BigDecimal.TEN,date));
    }
    private JsonNode get(String path,int status) throws Exception {
        try (HttpClient client=HttpClient.newHttpClient()) {
            HttpResponse<String> response=client.send(HttpRequest.newBuilder(
                    URI.create("http://localhost:"+environment.getRequiredProperty("local.server.port")+path))
                    .header("Authorization","Bearer "+token).GET().build(),HttpResponse.BodyHandlers.ofString());
            assertThat(response.statusCode()).as(path+" -> "+response.body()).isEqualTo(status);
            return json.readTree(response.body());
        }
    }
    private BrapiQuoteResponse quote(String symbol,String currency,String price) {
        return new BrapiQuoteResponse(List.of(new BrapiQuoteResult(symbol,symbol,
                new BrapiQuoteData(symbol,currency,new BigDecimal(price),BigDecimal.ZERO))));
    }
    private BrapiHistoricalResponse historical(String symbol) {
        long timestamp=first.atStartOfDay(ZoneId.of("America/Sao_Paulo")).toEpochSecond();
        return new BrapiHistoricalResponse(List.of(new BrapiHistoricalResult(symbol,symbol,false,
                new BrapiHistoricalData("1d","1mo",List.of(new BrapiHistoricalPrice(
                        timestamp,BigDecimal.TEN,BigDecimal.TEN,BigDecimal.TEN,BigDecimal.TEN,1L,BigDecimal.TEN))))),
                "2026-09-02T15:00:00Z",0L);
    }
}
