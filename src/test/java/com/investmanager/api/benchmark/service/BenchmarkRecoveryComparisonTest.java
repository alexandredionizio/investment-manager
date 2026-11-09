package com.investmanager.api.benchmark.service;

import com.investmanager.api.benchmark.calculator.*;
import com.investmanager.api.benchmark.client.bcb.*;
import com.investmanager.api.benchmark.model.BenchmarkType;
import com.investmanager.api.quote.client.BrapiClient;
import com.investmanager.api.quote.client.dto.*;
import com.investmanager.api.portfoliohistory.model.PortfolioHistoryPeriod;
import com.investmanager.api.portfoliohistory.returnrate.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.provider.EnumSource;
import java.math.BigDecimal;
import java.time.*;
import java.util.List;
import static com.investmanager.api.benchmark.service.BenchmarkHistoryFixture.q;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class BenchmarkRecoveryComparisonTest {
    @ParameterizedTest @EnumSource(value=PortfolioHistoryPeriod.class,names={"ONE_MONTH","ALL"})
    void recoveryRestoresCompoundingAndIndexReturnsWithDifferentPublicationCalendars(PortfolioHistoryPeriod period) {
        var f=new BenchmarkHistoryFixture();
        var from=LocalDate.of(2026,9,1);var to=from.plusDays(2);
        f.put(q(BenchmarkType.CDI,from.toString(),"0.05"));f.put(q(BenchmarkType.CDI,to.toString(),"0.07"));
        f.put(q(BenchmarkType.IBOV,from.toString(),"100"));
        var bcb=mock(BcbClient.class);var brapi=mock(BrapiClient.class);var portfolio=mock(PortfolioReturnService.class);
        when(bcb.getSeries(eq(12),any(),any())).thenReturn(List.of(new BcbSeriesPointResponse("01/09/2026","0.05"),new BcbSeriesPointResponse("02/09/2026","0.06"),new BcbSeriesPointResponse("03/09/2026","0.07")));
        var prices=List.of(from,to).stream().map(date->new BrapiIndexHistoricalPoint(date.atStartOfDay(ZoneId.of("America/Sao_Paulo")).toEpochSecond(),null,null,null,new BigDecimal(date.equals(from)?"100":"110"),null,null)).toList();
        when(brapi.getIndexHistoricalQuotes(eq("^BVSP"),any(),eq("1d"))).thenReturn(new BrapiIndexHistoricalResponse(List.of(new BrapiIndexHistoricalResult("^BVSP",prices))));
        when(portfolio.getReturns(1L,2L,period,null,null,to)).thenReturn(new PortfolioReturnResponse(1L,from,to,new BigDecimal("0.02"),List.of(
                new PortfolioReturnSeriesPoint(from,BigDecimal.ZERO,BigDecimal.ZERO),
                new PortfolioReturnSeriesPoint(from.plusDays(1),BigDecimal.ZERO,new BigDecimal("0.01")),
                new PortfolioReturnSeriesPoint(to,BigDecimal.ZERO,new BigDecimal("0.02")))));
        var service=new BenchmarkComparisonService(portfolio,new CdiBenchmarkService(bcb,f.synchronizer),new IbovBenchmarkService(brapi,f.synchronizer,f.clock),new CdiReturnCalculator(),new IbovReturnCalculator());
        var result=service.getComparison(1L,2L,period,null,null,to);
        assertThat(result.getFirst().cdiReturn()).isEqualByComparingTo("0");
        assertThat(result.get(1).cdiReturn()).isEqualByComparingTo("0.0006");
        assertThat(result.get(1).ibovReturn()).isEqualByComparingTo("0");
        assertThat(result.getLast().cdiReturn()).isEqualByComparingTo("0.00130042");
        assertThat(result.getLast().ibovReturn()).isEqualByComparingTo("0.1");
        assertThat(result.getLast().portfolioReturn()).isEqualByComparingTo("0.02");
        assertThat(f.rows).hasSize(5);
    }

    @Test
    void pendingPublicationAndFutureDatesRemainNullIndependentlyForEachBenchmark() {
        var today=LocalDate.of(2026,11,6);
        var portfolio=mock(PortfolioReturnService.class);
        var cdi=mock(CdiBenchmarkService.class);var ibov=mock(IbovBenchmarkService.class);
        when(cdi.load(any(),any())).thenReturn(List.of(q(BenchmarkType.CDI,today.minusDays(1).toString(),"0.05")));
        when(ibov.load(any(),any())).thenReturn(List.of(q(BenchmarkType.IBOV,today.minusDays(1).toString(),"100"),q(BenchmarkType.IBOV,today.toString(),"110")));
        when(portfolio.getReturns(1L,2L,PortfolioHistoryPeriod.ALL,null,null,today.plusDays(1)))
                .thenReturn(new PortfolioReturnResponse(1L,today.minusDays(1),today.plusDays(1),BigDecimal.ZERO,
                        List.of(today.minusDays(1),today,today.plusDays(1)).stream()
                                .map(d->new PortfolioReturnSeriesPoint(d,BigDecimal.ZERO,BigDecimal.ZERO)).toList()));
        var service=new BenchmarkComparisonService(portfolio,cdi,ibov,new CdiReturnCalculator(),new IbovReturnCalculator(),
                Clock.fixed(Instant.parse("2026-11-06T15:00:00Z"),ZoneId.of("America/Sao_Paulo")));
        var result=service.getComparison(1L,2L,PortfolioHistoryPeriod.ALL,null,null,today.plusDays(1));
        assertThat(result.getFirst().cdiReturn()).isEqualByComparingTo("0");
        assertThat(result.get(1).cdiReturn()).isNull();
        assertThat(result.get(1).ibovReturn()).isEqualByComparingTo("0.1");
        assertThat(result.getLast().cdiReturn()).isNull();
        assertThat(result.getLast().ibovReturn()).isNull();
        assertThat(result.getLast().portfolioReturn()).isEqualByComparingTo("0");
    }
}
