package com.investmanager.api.benchmark.service;

import com.investmanager.api.benchmark.client.bcb.*;
import com.investmanager.api.benchmark.model.BenchmarkType;
import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import java.util.List;
import static com.investmanager.api.benchmark.service.BenchmarkHistoryFixture.q;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class CdiBenchmarkServiceTest {
    final BenchmarkHistoryFixture f=new BenchmarkHistoryFixture();
    final BcbClient client=mock(BcbClient.class);
    final CdiBenchmarkService service=new CdiBenchmarkService(client,f.synchronizer);
    final LocalDate start=LocalDate.of(2026,9,21),end=start.plusDays(1);

    @Test void shouldLoadEntirePeriodWhenDatabaseIsEmpty() {
        when(client.getSeries(eq(12),any(),any())).thenReturn(List.of(new BcbSeriesPointResponse("22/09/2026","0.0551"),new BcbSeriesPointResponse("21/09/2026","0.055")));
        assertThat(service.load(start,end)).hasSize(2);
        verify(client).getSeries(12,LocalDate.of(2026,7,1),LocalDate.of(2026,9,30));
    }
    @Test void shouldNotCallBcbWhenRequestedPeriodIsAlreadyStored() {
        f.put(q(BenchmarkType.CDI,start.toString(),"0.055"));
        assertThat(service.load(start,start)).hasSize(1);verifyNoInteractions(client);
    }
    @Test void shouldFetchOnlyMissingQuarterAfterLastStoredQuote() {
        f.put(q(BenchmarkType.CDI,start.toString(),"0.055"));
        when(client.getSeries(eq(12),any(),any())).thenReturn(List.of(new BcbSeriesPointResponse("21/09/2026","0.055"),new BcbSeriesPointResponse("22/09/2026","0.0551")));
        assertThat(service.load(start,end)).hasSize(2);
        verify(f.writer).insertMissing(argThat(rows->rows.size()==1 && rows.getFirst().getDate().equals(end)));
        verify(client,times(1)).getSeries(eq(12),any(),any());
    }
    @Test void malformedPointDoesNotPersistEarlierValidPoint() {
        when(client.getSeries(eq(12),any(),any())).thenReturn(List.of(new BcbSeriesPointResponse("21/09/2026","0.055"),new BcbSeriesPointResponse("bad-date","0.05")));
        assertThatThrownBy(()->service.load(start,end)).isInstanceOf(RuntimeException.class);
        verifyNoInteractions(f.writer);
    }
}
