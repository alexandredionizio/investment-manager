package com.investmanager.api.valuation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import java.math.*;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static com.investmanager.api.valuation.ValuationTerms.*;

class FixedIncomeCalculatorTest {
    final IndexRateService indexes=mock(IndexRateService.class);
    final FinancialCalendar calendar=new FinancialCalendar();
    final FixedIncomeCalculator calculator=new FixedIncomeCalculator(indexes,calendar);
    final LocalDate start=LocalDate.of(2026,1,5);
    ValuationTerms terms(Instrument instrument,RateType rate,DayCount dayCount,LocalDate end) {
        return new ValuationTerms(instrument,rate,new BigDecimal("10"),new BigDecimal("110"),dayCount,start,end,2,null,true,true);
    }
    @Test void prefixAccumulatesAnnualRateAndStopsAtMaturity() {
        var t=terms(Instrument.CDB,RateType.PRE_FIXED,DayCount.ACT_365,start.plusDays(365));
        assertThat(calculator.factor(t,start.plusDays(365))).isCloseTo(new BigDecimal("1.10"),within(new BigDecimal("0.00000000000000000001")));
        assertThat(calculator.factor(t,start.plusDays(800))).isEqualByComparingTo(calculator.factor(t,start.plusDays(365)));
        verifyNoInteractions(indexes);
    }
    @Test void businessConventionUses252CompoundingDays() {
        LocalDate end=start.plusDays(1);int count=1;
        while(count<252){end=end.plusDays(1);count=calendar.days(start,end);}
        assertThat(calculator.factor(terms(Instrument.CDB,RateType.PRE_FIXED,DayCount.BUSINESS_252,end),end))
                .isCloseTo(new BigDecimal("1.10"),within(new BigDecimal("1E-20")));
    }
    @Test void cdiUsesEachDailyRateAndContractPercentage() {
        LocalDate end=start.plusDays(2);
        when(indexes.rates(12,start,end.minusDays(1))).thenReturn(Map.of(start,new BigDecimal("0.05"),start.plusDays(1),new BigDecimal("0.06")));
        assertThat(calculator.factor(terms(Instrument.CDB,RateType.CDI_PERCENT,DayCount.BUSINESS_252,end),end))
                .isEqualByComparingTo("1.001210363");
    }
    @Test void missingCdiDoesNotInventRateOrAssumeHoliday() {
        LocalDate end=start.plusDays(2);when(indexes.rates(anyInt(),any(),any())).thenReturn(Map.of(start,new BigDecimal("0.05")));
        assertThatThrownBy(()->calculator.factor(terms(Instrument.CDB,RateType.CDI_PERCENT,DayCount.BUSINESS_252,end),end))
                .isInstanceOf(ValuationUnavailableException.class).hasMessageContaining("2026-01-06");
    }
    @Test void ipcaUsesObservedMonthlyInflationAndRealInterest() {
        var t=new ValuationTerms(Instrument.LCI,RateType.IPCA_PLUS,BigDecimal.ZERO,null,DayCount.ACT_365,start,start.plusYears(1),2,null,true,true);
        when(indexes.rates(anyInt(),any(),any())).thenReturn(Map.of(LocalDate.of(2025,11,1),new BigDecimal("0.50")));
        assertThat(calculator.factor(t,start.plusMonths(1))).isCloseTo(new BigDecimal("1.005"),within(new BigDecimal("1E-20")));
        assertThat(calculator.factor(t,start.plusDays(15)).pow(31,DecimalFactors.MC))
                .isCloseTo(new BigDecimal("1.005").pow(15,DecimalFactors.MC),within(new BigDecimal("1E-20")));
    }
    @Test void unavailableIpcaCannotBecomeProjection() {
        when(indexes.rates(anyInt(),any(),any())).thenReturn(Map.of());
        var t=terms(Instrument.LCA,RateType.IPCA_PLUS,DayCount.ACT_365,start.plusYears(1));
        assertThatThrownBy(()->calculator.factor(t,start.plusDays(1))).isInstanceOf(ValuationUnavailableException.class);
    }
    @ParameterizedTest @CsvSource({"180,1077.50","181,1080.00","360,1080.00","361,1082.50","720,1082.50","721,1085.00","15,1038.75","30,1077.50"})
    void taxesUseHoldingDaysAndIofBeforeIr(int days,String expected) {
        var t=terms(Instrument.CDB,RateType.PRE_FIXED,DayCount.ACT_365,start.plusYears(5));
        assertThat(calculator.netValue(t,new BigDecimal("1100"),new BigDecimal("1000"),start.plusDays(days))).isEqualByComparingTo(expected);
    }
    @ParameterizedTest @EnumSource(value=Instrument.class,names={"LCI","LCA"})
    void exemptIndividualProductsPreserveGross(Instrument instrument) {
        assertThat(calculator.netValue(terms(instrument,RateType.PRE_FIXED,DayCount.ACT_365,start.plusYears(1)),new BigDecimal("1100"),new BigDecimal("1000"),start.plusDays(15))).isEqualByComparingTo("1100");
    }
    @Test void missingTaxProfileDoesNotInventNetValue() {
        var t=new ValuationTerms(Instrument.CDB,RateType.PRE_FIXED,BigDecimal.TEN,null,DayCount.ACT_365,start,start.plusYears(1),null,null,null,true);
        assertThat(calculator.netValue(t,new BigDecimal("1100"),new BigDecimal("1000"),start.plusDays(30))).isNull();
    }
    @ParameterizedTest @ValueSource(strings={"2026-02-16","2026-02-17","2026-04-03","2026-06-04","2026-11-20","2026-11-08"})
    void nationalHolidaysAndWeekendAreExcluded(String date) {assertThat(calendar.businessDay(LocalDate.parse(date))).isFalse();}
    @Test void blackAwarenessHolidayAppliesOnlyFrom2024() {
        assertThat(calendar.businessDay(LocalDate.of(2023,11,20))).isTrue();
        assertThat(calendar.businessDay(LocalDate.of(2024,11,20))).isFalse();
    }
    @Test void missingTermsAndInvalidRendaDatesAreRejected() {
        assertThatThrownBy(()->new ValuationTerms(Instrument.CDB,null,null,null,null,null,start,null,null,null,true).validate()).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        assertThatThrownBy(()->new ValuationTerms(Instrument.TESOURO_RENDA,null,null,null,null,null,start.plusYears(30),null,start,null,true).validate()).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
    }
}
