package com.investmanager.api.quote.service;

import com.investmanager.api.asset.*;
import com.investmanager.api.asset.repository.AssetRepository;
import com.investmanager.api.quote.client.BrapiClient;
import com.investmanager.api.quote.client.dto.*;
import com.investmanager.api.quote.client.mapper.*;
import com.investmanager.api.quote.exception.*;
import com.investmanager.api.portfoliohistory.model.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.data.redis.core.*;
import org.springframework.web.client.ResourceAccessException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AssetQuoteSupportTest {
    AssetRepository assets;
    BrapiClient provider;
    StringRedisTemplate redis;
    ValueOperations<String, String> cache;
    AssetQuoteSupport support;
    QuoteService current;
    HistoricalQuoteService history;

    @BeforeEach void setup() {
        assets = mock(AssetRepository.class); provider = mock(BrapiClient.class);
        redis = mock(StringRedisTemplate.class); cache = mock(ValueOperations.class);
        support = new AssetQuoteSupport(assets);
        current = new QuoteService(provider, redis, support);
        history = new HistoricalQuoteService(provider, new BrapiHistoricalQuoteMapper(),
                new BrapiHistoricalRangeMapper(), support);
    }

    Asset registered(AssetType type, String exchange) {
        Asset asset = new Asset(); asset.setTicker("TEST11"); asset.setType(type); asset.setExchange(exchange);
        when(assets.findByTickerIgnoreCase("TEST11")).thenReturn(List.of(asset));
        return asset;
    }

    @ParameterizedTest @EnumSource(value=AssetType.class, names={"STOCK","FII","ETF"})
    void supportsOnlyImplementedB3Classes(AssetType type) {
        registered(type, " b3 ");
        assertThat(support.requireSupported(" test11 ")).isEqualTo("TEST11");
    }

    @ParameterizedTest @EnumSource(value=AssetType.class, names={"CRYPTO","FIXED_INCOME","TREASURE"})
    void unsupportedClassesNeverReachProviderOrCacheEvenWithB3Ticker(AssetType type) {
        registered(type, "B3");
        assertUnavailableForAllQuoteMethods();
        verifyNoInteractions(redis, provider);
    }

    @ParameterizedTest @NullAndEmptySource @ValueSource(strings={"NASDAQ","NYSE","BINANCE"})
    void unidentifiedOrForeignExchangeCannotBeValuedInBRL(String exchange) {
        registered(AssetType.STOCK, exchange);
        assertUnavailableForAllQuoteMethods();
        verifyNoInteractions(redis, provider);
    }

    @Test void unregisteredSymbolIsNotAssumedToBeB3() {
        assertUnavailableForAllQuoteMethods();
        verifyNoInteractions(redis, provider);
    }

    @Test void missingTypeIsUnavailable() {
        registered(null, "B3");
        assertUnavailableForAllQuoteMethods();
        verifyNoInteractions(redis, provider);
    }

    @Test void conflictingRegistrationsCannotReuseTickerCache() {
        Asset a = registered(AssetType.STOCK, "B3");
        Asset b = new Asset(); b.setTicker("test11"); b.setType(AssetType.CRYPTO); b.setExchange("B3");
        when(assets.findByTickerIgnoreCase("TEST11")).thenReturn(List.of(a,b));
        assertUnavailableForAllQuoteMethods();
        verifyNoInteractions(redis, provider);
    }

    @Test void repositoryFailureIsNotClassifiedAsMissingQuote() {
        when(assets.findByTickerIgnoreCase(anyString())).thenThrow(new IllegalStateException("database down"));
        assertThatThrownBy(() -> current.getCurrentPrice("TEST11")).isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(redis, provider);
    }

    @ParameterizedTest @ValueSource(strings={"USD","EUR",""})
    void foreignOrMissingCurrencyCannotBePresentedAsReais(String currency) {
        registered(AssetType.STOCK, "B3");
        when(redis.opsForValue()).thenReturn(cache);
        when(provider.getQuote("TEST11")).thenReturn(quote(currency, "20"));
        assertThatThrownBy(() -> current.getCurrentPrice("TEST11")).isInstanceOf(IllegalStateException.class);
        verify(cache, never()).set(anyString(), anyString(), any(java.time.Duration.class));
    }

    @Test void realZeroPriceRemainsZero() {
        registered(AssetType.ETF, "B3"); when(redis.opsForValue()).thenReturn(cache);
        when(provider.getQuote("TEST11")).thenReturn(quote("BRL", "0"));
        assertThat(current.getCurrentPrice("TEST11")).isEqualByComparingTo("0");
        verify(cache).set(eq("quote:brl:v2:TEST11"), eq("0"), any(java.time.Duration.class));
    }

    @Test void technicalProviderFailureIsNotAbsenceOrZero() {
        registered(AssetType.FII, "B3"); when(redis.opsForValue()).thenReturn(cache);
        when(provider.getQuote("TEST11")).thenThrow(new ResourceAccessException("timeout"));
        assertThatThrownBy(() -> current.getCurrentPrice("TEST11")).isInstanceOf(ResourceAccessException.class);
        verify(cache, never()).set(anyString(), anyString(), any(java.time.Duration.class));
    }

    @Test void emptySupportedQuoteRemainsExplicitMissingQuote() {
        registered(AssetType.FII, "B3"); when(redis.opsForValue()).thenReturn(cache);
        when(provider.getQuote("TEST11")).thenReturn(new BrapiQuoteResponse(List.of()));
        assertThatThrownBy(() -> current.getCurrentPrice("TEST11")).isInstanceOf(QuoteNotFoundException.class);
    }

    @Test void negativePriceIsTechnicalFailure() {
        registered(AssetType.STOCK, "B3"); when(redis.opsForValue()).thenReturn(cache);
        when(provider.getQuote("TEST11")).thenReturn(quote("BRL", "-1"));
        assertThatThrownBy(() -> current.getCurrentPrice("TEST11")).isInstanceOf(IllegalStateException.class);
    }

    private BrapiQuoteResponse quote(String currency, String price) {
        return new BrapiQuoteResponse(List.of(new BrapiQuoteResult("TEST11", "TEST11",
                new BrapiQuoteData("Test", currency, new BigDecimal(price), BigDecimal.ZERO))));
    }

    private void assertUnavailableForAllQuoteMethods() {
        assertThatThrownBy(() -> current.getCurrentPrice("TEST11")).isInstanceOf(UnsupportedAssetQuoteException.class);
        assertThatThrownBy(() -> history.getHistoricalQuotes("TEST11", PortfolioHistoryPeriod.ALL,
                PortfolioHistoryGranularity.DAILY)).isInstanceOf(UnsupportedAssetQuoteException.class);
        assertThatThrownBy(() -> history.getHistoricalQuotes("TEST11", LocalDate.of(2026,9,1),
                LocalDate.of(2026,9,2), PortfolioHistoryGranularity.DAILY)).isInstanceOf(UnsupportedAssetQuoteException.class);
    }
}
