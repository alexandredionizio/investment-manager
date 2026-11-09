package com.investmanager.api.benchmark.service;

import com.investmanager.api.benchmark.model.BenchmarkType;
import com.investmanager.api.quote.client.BrapiClient;
import com.investmanager.api.quote.client.dto.*;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.*;
import java.util.List;
import static com.investmanager.api.benchmark.service.BenchmarkHistoryFixture.q;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class IbovBenchmarkServiceTest {
    final BenchmarkHistoryFixture f=new BenchmarkHistoryFixture();
    final BrapiClient client=mock(BrapiClient.class);
    final IbovBenchmarkService service=new IbovBenchmarkService(client,f.synchronizer,f.clock);
    final LocalDate start=LocalDate.of(2026,9,1);
    private BrapiIndexHistoricalResponse response(String symbol, String date, String value) {
        var point=new BrapiIndexHistoricalPoint(LocalDate.parse(date).atStartOfDay(ZoneId.of("America/Sao_Paulo")).toEpochSecond(),null,null,null,new BigDecimal(value),null,null);
        return new BrapiIndexHistoricalResponse(List.of(new BrapiIndexHistoricalResult(symbol,List.of(point))));
    }
    @Test void shouldLoadEntirePeriodWhenDatabaseIsEmpty() {
        when(client.getIndexHistoricalQuotes(eq("^BVSP"),any(),eq("1d"))).thenReturn(response("^BVSP","2026-09-01","141000"));
        assertThat(service.load(start,start)).hasSize(1);
        verify(client).getIndexHistoricalQuotes("^BVSP","6mo","1d");
    }
    @Test void shouldNotCallBrapiWhenRequestedPeriodIsAlreadyStored() {
        f.put(q(BenchmarkType.IBOV,start.toString(),"141000"));
        assertThat(service.load(start,start)).hasSize(1); verifyNoInteractions(client);
    }
    @Test void shouldFetchOnlyMissingQuarterAfterLastStoredQuote() {
        f.put(q(BenchmarkType.IBOV,"2026-06-30","140000"));
        when(client.getIndexHistoricalQuotes(eq("^BVSP"),any(),eq("1d"))).thenReturn(response("^BVSP","2026-07-01","141000"));
        assertThat(service.load(LocalDate.of(2026,6,30),LocalDate.of(2026,7,1))).hasSize(2);
        verify(client,times(1)).getIndexHistoricalQuotes("^BVSP","6mo","1d");
    }
    @Test void shouldConvertUnixTimestampToSaoPauloLocalDate() {
        var point=new BrapiIndexHistoricalPoint(Instant.parse("2026-09-02T01:00:00Z").getEpochSecond(),null,null,null,new BigDecimal("143500.75"),null,null);
        when(client.getIndexHistoricalQuotes(eq("^BVSP"),any(),eq("1d"))).thenReturn(new BrapiIndexHistoricalResponse(List.of(new BrapiIndexHistoricalResult("^BVSP",List.of(point)))));
        assertThat(service.load(start,start).getFirst().getDate()).isEqualTo(start);
    }
    @Test void shouldRequestLongEnoughRangeForHistoricalPeriod() {
        when(client.getIndexHistoricalQuotes(eq("^BVSP"),any(),eq("1d"))).thenReturn(new BrapiIndexHistoricalResponse(List.of()));
        assertThat(service.load(LocalDate.of(2020,1,1),LocalDate.of(2020,1,31))).isEmpty();
        verify(client).getIndexHistoricalQuotes("^BVSP","10y","1d");
    }
    @Test void shouldRejectInvalidProviderResponseInsteadOfReturningEmptyBenchmark() {
        when(client.getIndexHistoricalQuotes(eq("^BVSP"),any(),eq("1d"))).thenReturn(null,new BrapiIndexHistoricalResponse(null),response("OTHER","2026-09-01","100"));
        for(int i=0;i<3;i++) assertThatThrownBy(()->service.load(start,start)).isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(f.writer);
    }
    @Test void shouldAllowLegitimatelyEmptyBenchmarkResponse() {
        when(client.getIndexHistoricalQuotes(eq("^BVSP"),any(),eq("1d"))).thenReturn(new BrapiIndexHistoricalResponse(List.of()));
        assertThat(service.load(start,start)).isEmpty();
    }
    @Test void shouldReuseOneRangeResponseAcrossHistoricalQuarters() {
        when(client.getIndexHistoricalQuotes(eq("^BVSP"),any(),eq("1d"))).thenReturn(new BrapiIndexHistoricalResponse(List.of()));
        assertThat(service.load(LocalDate.of(2020,1,1),LocalDate.of(2020,9,30))).isEmpty();
        verify(client,times(1)).getIndexHistoricalQuotes("^BVSP","10y","1d");
    }
}
