package com.investmanager.api.valuation;
import com.investmanager.api.benchmark.client.bcb.*;
import com.investmanager.api.shared.integration.*;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
class IndexRateServiceTest {
    final BcbClient bcb=mock(BcbClient.class);
    final MarketDataCache cache=mock(MarketDataCache.class);
    final IndexRateService service=new IndexRateService(bcb,cache,new IntegrationProperties());
    final LocalDate start=LocalDate.of(2026,1,5);
    @Test void observedRatesAreCachedWithoutFillingGaps() {
        when(bcb.getSeries(12,start,start.plusDays(1))).thenReturn(List.of(new BcbSeriesPointResponse("05/01/2026","0.05")));
        var rates=service.rates(12,start,start.plusDays(1));
        assertThat(rates).hasSize(1);assertThat(rates).doesNotContainKey(start.plusDays(1));
        verify(cache).put(contains(":12:"),eq("2026-01-05=0.05;"),any());
    }
    @Test void validCachedRateDoesNotCallBcb() {
        when(cache.get(anyString())).thenReturn("2026-01-05=0.05;");
        assertThat(service.rates(12,start,start)).hasSize(1);verifyNoInteractions(bcb);
    }
    @Test void duplicateDatesAreRejected() {
        when(bcb.getSeries(anyInt(),any(),any())).thenReturn(List.of(new BcbSeriesPointResponse("05/01/2026","0.05"),new BcbSeriesPointResponse("05/01/2026","0.06")));
        assertThatThrownBy(()->service.rates(12,start,start)).isInstanceOf(ValuationUnavailableException.class);
        verify(cache,never()).put(anyString(),anyString(),any());
    }
    @Test void requestsAreSplitIntoAnnualWindows() {
        when(bcb.getSeries(anyInt(),any(),any())).thenReturn(List.of());
        assertThat(service.rates(12,start,start.plusYears(2))).isEmpty();
        verify(bcb).getSeries(12,start,start.plusYears(1).minusDays(1));
        verify(bcb).getSeries(12,start.plusYears(1),start.plusYears(2).minusDays(1));
        verify(bcb).getSeries(12,start.plusYears(2),start.plusYears(2));
    }
}
