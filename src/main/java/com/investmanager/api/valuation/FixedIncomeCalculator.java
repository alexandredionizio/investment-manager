package com.investmanager.api.valuation;

import org.springframework.stereotype.Service;
import java.math.*;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import static com.investmanager.api.valuation.DecimalFactors.*;
import static com.investmanager.api.valuation.ValuationTerms.*;

@Service
public class FixedIncomeCalculator {
    private final IndexRateService indexes;
    private final FinancialCalendar calendar;
    public FixedIncomeCalculator(IndexRateService indexes, FinancialCalendar calendar) { this.indexes=indexes;this.calendar=calendar; }
    public BigDecimal factor(ValuationTerms terms, LocalDate date) {
        LocalDate start=terms.applicationDate(),end=date.isAfter(terms.maturityDate())?terms.maturityDate():date;
        if(end.isBefore(start))throw new ValuationUnavailableException("Aplicação ainda não iniciada");
        if(start.equals(end))return BigDecimal.ONE;
        if(terms.rateType()==RateType.CDI_PERCENT) {
            var rates=indexes.rates(12,start,end.minusDays(1));
            BigDecimal factor=BigDecimal.ONE;
            for(LocalDate day=start;day.isBefore(end);day=day.plusDays(1)) {
                if(!calendar.businessDay(day))continue;
                BigDecimal rate=rates.get(day);
                if(rate==null)throw new ValuationUnavailableException("CDI incompleto em "+day);
                factor=factor.multiply(BigDecimal.ONE.add(rate.multiply(terms.cdiPercent(),MC).movePointLeft(4),MC),MC);
            }
            return factor;
        }
        int days=terms.dayCount()==DayCount.BUSINESS_252?calendar.days(start,end):Math.toIntExact(ChronoUnit.DAYS.between(start,end));
        BigDecimal factor=power(BigDecimal.ONE.add(terms.annualRate().movePointLeft(2)),days,terms.dayCount()==DayCount.BUSINESS_252?252:365);
        if(terms.rateType()==RateType.IPCA_PLUS) factor=factor.multiply(inflation(terms,start,end),MC);
        return factor;
    }
    private BigDecimal inflation(ValuationTerms terms,LocalDate start,LocalDate end) {
        int lag=terms.ipcaLagMonths();
        var rates=indexes.rates(433,start.withDayOfMonth(1).minusMonths(lag),end.withDayOfMonth(1));
        BigDecimal factor=BigDecimal.ONE;
        // Contractual anniversary, observed monthly IPCA, geometric calendar-day pro rata.
        for(LocalDate from=start;from.isBefore(end);from=from.plusMonths(1)) {
            LocalDate next=from.plusMonths(1),to=next.isAfter(end)?end:next;
            LocalDate month=from.withDayOfMonth(1).minusMonths(lag);
            BigDecimal rate=rates.get(month);
            if(rate==null)throw new ValuationUnavailableException("IPCA não publicado para "+YearMonth.from(month));
            factor=factor.multiply(power(BigDecimal.ONE.add(rate.movePointLeft(2)),
                    Math.toIntExact(ChronoUnit.DAYS.between(from,to)),Math.toIntExact(ChronoUnit.DAYS.between(from,next))),MC);
        }
        return factor;
    }
    public BigDecimal netValue(ValuationTerms terms, BigDecimal gross, BigDecimal principal, LocalDate date) {
        if(!Boolean.TRUE.equals(terms.brazilResidentIndividual()))return null;
        if(terms.instrument()==Instrument.LCI||terms.instrument()==Instrument.LCA)return money(gross);
        long days=ChronoUnit.DAYS.between(terms.applicationDate(),date.isAfter(terms.maturityDate())?terms.maturityDate():date);
        BigDecimal gain=gross.subtract(principal).max(BigDecimal.ZERO);
        int[] iof={100,96,93,90,86,83,80,76,73,70,66,63,60,56,53,50,46,43,40,36,33,30,26,23,20,16,13,10,6,3};
        BigDecimal iofTax=days<30?gain.multiply(BigDecimal.valueOf(iof[(int)Math.max(0,days)]).movePointLeft(2),MC):BigDecimal.ZERO;
        BigDecimal rate=new BigDecimal(days<=180?"0.225":days<=360?"0.20":days<=720?"0.175":"0.15");
        return money(gross.subtract(iofTax).subtract(gain.subtract(iofTax).multiply(rate,MC)));
    }
    public static BigDecimal money(BigDecimal value) { return value.setScale(2,RoundingMode.HALF_UP); }
}
