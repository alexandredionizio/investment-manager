package com.investmanager.api.asset.discovery;
import com.investmanager.api.asset.AssetType;
import com.investmanager.api.shared.integration.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;
import static org.hamcrest.Matchers.containsString;
class BrapiCatalogClientTest {
    ExternalCalls calls;
    MarketDataCache cache;
    MockRestServiceServer server;
    BrapiCatalogClient client;
    @BeforeEach void setup() {
        var policy = new IntegrationProperties(); policy.getBrapi().setMaxAttempts(1);
        calls = new ExternalCalls(policy); cache = mock(MarketDataCache.class);
        var builder = RestClient.builder().baseUrl("https://catalog.test");
        server = MockRestServiceServer.bindTo(builder).build();
        client = new BrapiCatalogClient(builder.build(), calls, cache, JsonMapper.builder().build());
    }
    @AfterEach void close() { calls.close(); }
    String row(String ticker, String family, String subtype, boolean active) {
        return """
            {"symbol":"%s","name":"Instrumento de teste","assetType":"%s","subType":"%s","exchange":"B3","currency":"BRL","isActive":%s}
            """.formatted(ticker, family, subtype, active);
    }
    String page(String rows, int page, boolean next) { return "{\"results\":[" + rows + "],\"pagination\":{\"page\":" + page + ",\"hasNextPage\":" + next + "}}"; }
    void searchResponse(String body) { server.expect(requestTo(containsString("/api/v2/tickers?search="))).andRespond(withSuccess(body, MediaType.APPLICATION_JSON)); }
    @Test void searchesNamesWithBoundedPaginationAndFiltersUnsupportedClasses() {
        searchResponse(page(row("PETR4","stock","stock",true)+","+row("HGLG11","fund","fii",true)+","+
                row("BOVA11","fund","etf",true)+","+row("AAPL34","bdr","bdr",true)+","+row("OLDT3","stock","stock",false),2,true));
        var result = client.search("Petrobras",2,10);
        assertThat(result.items()).extracting(InstrumentEvidence::type).containsExactly(AssetType.STOCK,AssetType.FII,AssetType.ETF);
        assertThat(result.page()).isEqualTo(2); assertThat(result.hasNextPage()).isTrue();
        verify(cache).put(anyString(),anyString(),eq(java.time.Duration.ofSeconds(60)));
        server.verify();
    }
    @Test void usesCacheOnlyForSearch() {
        when(cache.get(anyString())).thenReturn(page(row("PETR4","stock","stock",true),1,false));
        assertThat(client.search("petr",1,10).items()).hasSize(1);
        searchResponse(page(row("PETR4","stock","stock",false),1,false));
        assertThatThrownBy(() -> client.confirm("PETR4")).isInstanceOf(InstrumentException.class)
                .extracting(e -> ((InstrumentException)e).code()).isEqualTo("INSTRUMENT_INELIGIBLE");
        server.verify();
    }
    @ParameterizedTest @ValueSource(strings={"", "P", "BTC", "PETR4F", "PETR4;DROP"})
    void rejectsInvalidPurchaseTickersWithoutHttp(String ticker) {
        assertThatThrownBy(() -> client.confirm(ticker)).isInstanceOf(InstrumentException.class);
        server.verify();
    }
    @Test void validatesPaginationAndQueryBeforeReadingCache() {
        assertThatThrownBy(() -> client.search("P",1,10)).isInstanceOf(InstrumentException.class);
        assertThatThrownBy(() -> client.search("PETR",0,10)).isInstanceOf(InstrumentException.class);
        assertThatThrownBy(() -> client.search("PETR",1,21)).isInstanceOf(InstrumentException.class);
        verifyNoInteractions(cache);
    }
    @ParameterizedTest @ValueSource(strings={"{}", "{\"results\":null}", "{\"results\":[],\"pagination\":{\"page\":1}}"})
    void malformedPayloadIsNotEmptySearch(String payload) {
        searchResponse(payload);
        assertThatThrownBy(() -> client.search("PETR",1,10)).isInstanceOf(InstrumentException.class)
                .extracting(e -> ((InstrumentException)e).status()).isEqualTo(502);
        verify(cache,never()).put(anyString(),anyString(),any());
    }
    @Test void providerUnauthorizedIsCatalogFailureNotUserSessionFailure() {
        server.expect(requestTo(containsString("/tickers?"))).andRespond(withStatus(HttpStatus.UNAUTHORIZED).body("secret-provider-body"));
        assertThatThrownBy(() -> client.search("PETR",1,10)).isInstanceOf(InstrumentException.class)
                .satisfies(e -> { assertThat(((InstrumentException)e).status()).isEqualTo(502); assertThat(e.getMessage()).doesNotContain("secret"); });
    }
    @Test void confirmsOnlyExactTickerAndConfirmedOneToOneAliases() {
        searchResponse(page(row("BHIA3","stock","stock",true),1,false));
        server.expect(requestTo(containsString("/tickers/renames?symbols=BHIA3"))).andRespond(withSuccess("""
            {"results":[{"oldSymbol":"VIIA3","newSymbol":"BHIA3","canonicalSymbol":"BHIA3","effectiveDate":"2023-09-20"}]}
            """,MediaType.APPLICATION_JSON));
        var evidence = client.confirm("bhia3");
        assertThat(evidence.ticker()).isEqualTo("BHIA3"); assertThat(evidence.isin()).isNull();
        assertThat(evidence.aliases()).extracting(InstrumentEvidence.Alias::ticker).containsExactly("VIIA3");
        verifyNoInteractions(cache); server.verify();
    }
    @ParameterizedTest @ValueSource(strings={"0.1", "1"})
    void conversionRequiresReviewAndNeverInfersQuantityAdjustment(String ratio) {
        searchResponse(page(row("BHIA3","stock","stock",true),1,false));
        server.expect(requestTo(containsString("/tickers/renames?"))).andRespond(withSuccess("""
            {"results":[{"oldSymbol":"VIIA3","newSymbol":"BHIA3","canonicalSymbol":"BHIA3","effectiveDate":"2023-09-20","conversionRatio":%s}]}
            """.formatted(ratio),MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> client.confirm("BHIA3")).isInstanceOf(InstrumentException.class)
                .extracting(e -> ((InstrumentException)e).status()).isEqualTo(409);
    }
    @Test void aSimilarSymbolCannotAuthorizeAnUnknownTicker() {
        searchResponse(page(row("PETR4","stock","stock",true),1,false));
        assertThatThrownBy(() -> client.confirm("PETR3")).isInstanceOf(InstrumentException.class);
        server.verify();
    }
}
