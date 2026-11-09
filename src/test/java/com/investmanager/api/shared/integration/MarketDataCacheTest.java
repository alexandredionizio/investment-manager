package com.investmanager.api.shared.integration;

import com.investmanager.api.quote.client.BrapiClient;
import com.investmanager.api.quote.client.dto.*;
import com.investmanager.api.quote.client.mapper.*;
import com.investmanager.api.quote.service.*;
import com.investmanager.api.portfoliohistory.model.PortfolioHistoryGranularity;
import org.junit.jupiter.api.*;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.utility.DockerImageName;
import java.math.BigDecimal;
import java.time.*;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@Testcontainers
class MarketDataCacheTest {
    @Container static GenericContainer<?> redisContainer=new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);
    static LettuceConnectionFactory factory;
    StringRedisTemplate redis;
    BrapiClient provider;
    AssetQuoteSupport support;
    IntegrationProperties properties;
    @BeforeAll static void connection() {
        factory=new LettuceConnectionFactory(redisContainer.getHost(),redisContainer.getMappedPort(6379)); factory.afterPropertiesSet(); factory.start();
    }
    @AfterAll static void close() { if (factory!=null) factory.destroy(); }
    @BeforeEach void setup() {
        redis=new StringRedisTemplate(factory);
        try (var connection=factory.getConnection()) { connection.serverCommands().flushDb(); }
        provider=mock(BrapiClient.class); support=mock(AssetQuoteSupport.class);
        when(support.requireSupported("TEST11")).thenReturn("TEST11");
        when(provider.getQuote("TEST11")).thenReturn(new BrapiQuoteResponse(List.of(
                new BrapiQuoteResult("TEST11","TEST11",new BrapiQuoteData("test","BRL",BigDecimal.TEN,BigDecimal.ZERO)))));
        properties=new IntegrationProperties();
    }
    @Test void quoteCacheMissHitAndActualRedisExpiry() throws Exception {
        properties.setQuoteTtlSeconds(1);
        var service=new QuoteService(provider,new MarketDataCache(redis),support,properties);
        assertThat(service.getCurrentPrice("TEST11")).isEqualByComparingTo("10");
        assertThat(service.getCurrentPrice("TEST11")).isEqualByComparingTo("10");
        verify(provider).getQuote("TEST11");
        Thread.sleep(1100);
        assertThat(service.getCurrentPrice("TEST11")).isEqualByComparingTo("10");
        verify(provider,times(2)).getQuote("TEST11");
    }
    @Test void historyCacheUsesIntervalAndExpires() throws Exception {
        properties.setHistoryTtlSeconds(1);
        LocalDate start=LocalDate.of(2026,9,1), end=start.plusDays(1);
        when(provider.getHistoricalQuotes(anyString(),any(LocalDate.class),any(LocalDate.class),eq("1d"))).thenReturn(
                new BrapiHistoricalResponse(List.of(new BrapiHistoricalResult("TEST11","TEST11",false,
                    new BrapiHistoricalData("1d","1mo",List.of(new BrapiHistoricalPrice(1788274800L,null,null,null,BigDecimal.TEN,null,null))))), null, null));
        var service=new HistoricalQuoteService(provider,new BrapiHistoricalQuoteMapper(),new BrapiHistoricalRangeMapper(),support,new MarketDataCache(redis),properties);
        var first=service.getHistoricalQuotes("TEST11",start,end,PortfolioHistoryGranularity.DAILY);
        assertThat(service.getHistoricalQuotes("TEST11",start,end,PortfolioHistoryGranularity.DAILY)).isEqualTo(first);
        verify(provider).getHistoricalQuotes("TEST11",start,end,"1d");
        service.getHistoricalQuotes("TEST11",start,end.plusDays(1),PortfolioHistoryGranularity.DAILY);
        verify(provider).getHistoricalQuotes("TEST11",start,end.plusDays(1),"1d");
        Thread.sleep(1100);
        service.getHistoricalQuotes("TEST11",start,end,PortfolioHistoryGranularity.DAILY);
        verify(provider,times(2)).getHistoricalQuotes("TEST11",start,end,"1d");
    }
    @Test void invalidProviderResponseIsNeverCached() {
        when(provider.getQuote("TEST11")).thenReturn(null);
        var service=new QuoteService(provider,new MarketDataCache(redis),support,properties);
        assertThatThrownBy(() -> service.getCurrentPrice("TEST11")).isInstanceOf(IllegalStateException.class);
        assertThat(redis.opsForValue().get("quote:brl:v2:TEST11")).isNull();
    }
    @Test void corruptEntryIsMissAndUnsupportedAssetCannotUseCache() {
        redis.opsForValue().set("quote:brl:v2:TEST11","bad");
        var service=new QuoteService(provider,new MarketDataCache(redis),support,properties);
        assertThat(service.getCurrentPrice("TEST11")).isEqualByComparingTo("10");
        when(support.requireSupported("TEST11")).thenThrow(new com.investmanager.api.quote.exception.UnsupportedAssetQuoteException("TEST11","CRYPTO"));
        assertThatThrownBy(() -> service.getCurrentPrice("TEST11")).isInstanceOf(com.investmanager.api.quote.exception.UnsupportedAssetQuoteException.class);
        verify(provider).getQuote("TEST11");
    }
    @Test void redisReadAndWriteOutageDoesNotDiscardFreshPrice() {
        var broken=mock(StringRedisTemplate.class); ValueOperations<String,String> values=mock(ValueOperations.class);
        when(broken.opsForValue()).thenReturn(values);
        when(values.get(anyString())).thenThrow(new DataAccessResourceFailureException("offline"));
        doThrow(new DataAccessResourceFailureException("offline")).when(values).set(anyString(),anyString(),any(Duration.class));
        assertThat(new QuoteService(provider,new MarketDataCache(broken),support,properties).getCurrentPrice("TEST11")).isEqualByComparingTo("10");
    }
    @Test void cacheProgrammingErrorsAreNotSilenced() {
        var broken=mock(StringRedisTemplate.class);
        when(broken.opsForValue()).thenThrow(new InvalidDataAccessApiUsageException("bug"));
        assertThatThrownBy(() -> new MarketDataCache(broken).get("key")).isInstanceOf(InvalidDataAccessApiUsageException.class);
    }
}
