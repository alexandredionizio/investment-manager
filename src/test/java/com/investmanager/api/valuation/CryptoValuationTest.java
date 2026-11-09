package com.investmanager.api.valuation;

import com.investmanager.api.shared.integration.*;
import com.investmanager.api.position.dto.PositionResponse;
import com.investmanager.api.asset.AssetType;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.web.client.RestClient;
import org.springframework.test.web.client.MockRestServiceServer;
import java.math.BigDecimal;
import java.time.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;
import static org.springframework.http.MediaType.APPLICATION_JSON;

class CryptoValuationTest {
    private final Clock clock=Clock.fixed(Instant.parse("2026-11-08T12:00:00Z"),ZoneOffset.UTC);
    private final CryptoQuoteProvider provider=mock(CryptoQuoteProvider.class);
    private final MarketDataCache cache=mock(MarketDataCache.class);
    private final CryptoQuoteService service=new CryptoQuoteService(provider,cache,new IntegrationProperties(),clock);
    private MarketQuote price(String value) { return new MarketQuote(new BigDecimal(value),"BRL",clock.instant(),"CoinGecko"); }
    @ParameterizedTest @CsvSource({"BTC,BTC"," eth ,ETH"})
    void supportedCoinsHaveDecimalBrlQuotes(String ticker,SupportedCryptocurrency coin) {
        when(provider.quote(coin)).thenReturn(price("351234.12345678"));
        assertThat(service.quote(ticker).price()).isEqualByComparingTo("351234.12345678");
        verify(cache).put(eq("valuation:crypto:brl:v1:"+coin),contains("351234.12345678|"),eq(Duration.ofSeconds(300)));
    }
    @ParameterizedTest @NullAndEmptySource @ValueSource(strings={"SOL","ADA","XRP","BTCUSDT","bitcoin"})
    void unsupportedCoinsNeverCallProviderOrCache(String ticker) {
        assertThatThrownBy(()->service.quote(ticker)).isInstanceOf(ValuationUnavailableException.class);
        verifyNoInteractions(provider,cache);
    }
    @Test void cacheHitPreservesReferenceAndAvoidsProvider() {
        when(cache.get(anyString())).thenReturn("300000.12345678|2026-11-08T11:59:00Z");
        assertThat(service.quote("BTC").referenceTime()).isEqualTo(Instant.parse("2026-11-08T11:59:00Z"));
        verifyNoInteractions(provider);
    }
    @ParameterizedTest @ValueSource(strings={"corrupt","-1|2026-11-08T12:00:00Z","1|2026-11-07T12:00:00Z","1|2026-11-09T12:00:00Z"})
    void badCacheIsRefreshed(String value) {
        when(cache.get(anyString())).thenReturn(value);when(provider.quote(any())).thenReturn(price("20"));
        assertThat(service.quote("ETH").price()).isEqualByComparingTo("20");verify(cache).invalidEntry();
    }
    @Test void staleProviderIsNotCached() {
        when(provider.quote(any())).thenReturn(new MarketQuote(BigDecimal.TEN,"BRL",clock.instant().minusSeconds(901),"CoinGecko"));
        assertThatThrownBy(()->service.quote("BTC")).isInstanceOf(ValuationUnavailableException.class);
        verify(cache,never()).put(anyString(),anyString(),any());
    }
    @Test void currencyMustBeBrlAndPricePositive() {
        assertThatThrownBy(()->new MarketQuote(BigDecimal.TEN,"USD",clock.instant(),"x")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->price("0")).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void fractionalQuantityAndCostRemainExact() {
        var p=new PositionResponse(1L,"BTC",new BigDecimal("0.00000001"),BigDecimal.TEN,new BigDecimal("0.01"));
        var value=PositionValuationResponse.valued(p,AssetType.CRYPTO,new BigDecimal("300000"),p.quantity().multiply(new BigDecimal("300000")),"MARKET",clock.instant(),"test",null,null);
        assertThat(value.quantity()).isEqualByComparingTo("0.00000001");
        assertThat(value.totalCost()).isEqualByComparingTo("0.01");
        assertThat(value.currentValue()).isEqualByComparingTo("0.00");
        assertThat(value.profitLoss()).isEqualByComparingTo("-0.01");
    }
    @ParameterizedTest @CsvSource({"BTC,bitcoin","ETH,ethereum"})
    void adapterRequestsBrlAndPreservesDecimal(SupportedCryptocurrency coin,String id) {
        var builder=RestClient.builder().baseUrl("https://crypto.test");
        var server=MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://crypto.test/simple/price?ids="+id+"&vs_currencies=brl&include_last_updated_at=true&precision=full"))
                .andRespond(withSuccess("{\""+id+"\":{\"brl\":12345.12345678,\"last_updated_at\":1794139200}}",APPLICATION_JSON));
        try(varCalls calls=new varCalls()) {
            var quote=new CoinGeckoQuoteProvider(builder.build(),calls.value).quote(coin);
            assertThat(quote.price()).isEqualByComparingTo("12345.12345678");assertThat(quote.currency()).isEqualTo("BRL");
        }
        server.verify();
    }
    static class varCalls implements AutoCloseable {
        final ExternalCalls value=new ExternalCalls(new IntegrationProperties());
        public void close(){value.close();}
    }
}
