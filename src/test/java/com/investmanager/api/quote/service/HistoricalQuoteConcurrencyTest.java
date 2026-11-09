package com.investmanager.api.quote.service;

import com.investmanager.api.portfoliohistory.model.PortfolioHistoryGranularity;
import com.investmanager.api.quote.client.BrapiClient;
import com.investmanager.api.quote.client.dto.*;
import com.investmanager.api.quote.client.mapper.*;
import com.investmanager.api.shared.integration.*;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class HistoricalQuoteConcurrencyTest {
    final BrapiClient provider=mock(BrapiClient.class);
    final AssetQuoteSupport support=mock(AssetQuoteSupport.class);
    final MarketDataCache cache=mock(MarketDataCache.class);
    final IntegrationProperties policy=new IntegrationProperties();
    final CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);
    final AtomicBoolean fail=new AtomicBoolean();
    final ExternalIntegrationException failure=new ExternalIntegrationException(504,"timeout");
    final LocalDate start=LocalDate.of(2026,9,1),end=start.plusDays(1);
    final BrapiHistoricalResponse payload=new BrapiHistoricalResponse(List.of(new BrapiHistoricalResult(
            "TEST11","TEST11",false,new BrapiHistoricalData("1d","1mo",List.of(
                    new BrapiHistoricalPrice(1788318000L,null,null,null,BigDecimal.TEN,null,null))))),null,null);
    final HistoricalQuoteService service;

    HistoricalQuoteConcurrencyTest() {
        var values=new ConcurrentHashMap<String,String>();
        when(support.requireSupported(anyString())).thenAnswer(inv->inv.getArgument(0));
        when(cache.get(anyString())).thenAnswer(inv->values.get(inv.<String>getArgument(0)));
        doAnswer(inv->{values.put(inv.getArgument(0),inv.getArgument(1));return null;}).when(cache).put(anyString(),anyString(),any());
        when(provider.getHistoricalQuotes(anyString(),any(LocalDate.class),any(LocalDate.class),eq("1d")))
                .thenAnswer(inv->{
                    if(inv.<String>getArgument(0).equals("TEST11")) {
                        entered.countDown();
                        if(!release.await(5,TimeUnit.SECONDS)) throw new AssertionError("Probe did not release provider");
                    }
                    if(fail.get()) throw failure;
                    return payload;
                });
        service=new HistoricalQuoteService(provider,new BrapiHistoricalQuoteMapper(),new BrapiHistoricalRangeMapper(),support,cache,policy);
    }
    private List<com.investmanager.api.quote.model.HistoricalQuote> load(String symbol) {
        return service.getHistoricalQuotes(symbol,start,end,PortfolioHistoryGranularity.DAILY);
    }

    @Test void concurrentIdenticalRangesShareOneFetchAndThenUseCache() throws Exception {
        try(var pool=Executors.newVirtualThreadPerTaskExecutor()) {
            var owner=pool.submit(()->load("TEST11"));
            assertThat(entered.await(2,TimeUnit.SECONDS)).isTrue();
            var waiter=pool.submit(()->load("TEST11"));
            try {
                assertThatThrownBy(()->waiter.get(100,TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
                verify(provider,times(1)).getHistoricalQuotes(anyString(),any(LocalDate.class),any(LocalDate.class),eq("1d"));
            } finally {release.countDown();}
            assertThat(waiter.get(2,TimeUnit.SECONDS)).isEqualTo(owner.get(2,TimeUnit.SECONDS));
            assertThat(load("TEST11")).hasSize(1);
            verify(provider,times(1)).getHistoricalQuotes(anyString(),any(LocalDate.class),any(LocalDate.class),eq("1d"));
        }
    }

    @Test void unrelatedKeysAreNotSerializedByHistoryCoordination() throws Exception {
        try(var pool=Executors.newVirtualThreadPerTaskExecutor()) {
            var owner=pool.submit(()->load("TEST11"));
            assertThat(entered.await(2,TimeUnit.SECONDS)).isTrue();
            try {assertThat(pool.submit(()->load("OTHER11")).get(2,TimeUnit.SECONDS)).hasSize(1);}
            finally {release.countDown();}
            assertThat(owner.get(2,TimeUnit.SECONDS)).hasSize(1);
        }
    }

    @Test void providerFailureIsSharedButNotCachedAndNextRequestCanRecover() throws Exception {
        fail.set(true);
        try(var pool=Executors.newVirtualThreadPerTaskExecutor()) {
            var owner=pool.submit(()->load("TEST11"));
            assertThat(entered.await(2,TimeUnit.SECONDS)).isTrue();
            var waiter=pool.submit(()->load("TEST11"));
            try {assertThatThrownBy(()->waiter.get(100,TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);}
            finally {release.countDown();}
            assertThatThrownBy(()->owner.get(2,TimeUnit.SECONDS)).hasCause(failure);
            assertThatThrownBy(()->waiter.get(2,TimeUnit.SECONDS)).hasCause(failure);
            verify(cache,never()).put(anyString(),anyString(),any());
            verify(provider,times(1)).getHistoricalQuotes(anyString(),any(LocalDate.class),any(LocalDate.class),eq("1d"));
            fail.set(false);
            assertThat(load("TEST11")).hasSize(1);
            verify(provider,times(2)).getHistoricalQuotes(anyString(),any(LocalDate.class),any(LocalDate.class),eq("1d"));
        }
    }

    @Test void waitRespectsAggregateBudgetWithoutCancellingOwner() throws Exception {
        policy.setRequestBudgetMs(75);
        try(var pool=Executors.newVirtualThreadPerTaskExecutor()) {
            var owner=pool.submit(()->load("TEST11"));
            assertThat(entered.await(2,TimeUnit.SECONDS)).isTrue();
            try {
                var waiter=pool.submit(()->{
                    new IntegrationBudgetFilter(policy).doFilter(new MockHttpServletRequest(),new MockHttpServletResponse(),
                            (request,response)->assertThatThrownBy(()->load("TEST11"))
                                    .isInstanceOfSatisfying(ExternalIntegrationException.class,e->assertThat(e.status()).isEqualTo(504)));
                    return null;
                });
                waiter.get(2,TimeUnit.SECONDS);
                assertThat(owner.isDone()).isFalse();
            } finally {release.countDown();}
            assertThat(owner.get(2,TimeUnit.SECONDS)).hasSize(1);
            verify(provider,times(1)).getHistoricalQuotes(anyString(),any(LocalDate.class),any(LocalDate.class),eq("1d"));
        }
    }
}
