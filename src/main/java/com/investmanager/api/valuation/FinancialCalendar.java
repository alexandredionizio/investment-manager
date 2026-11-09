package com.investmanager.api.valuation;

import org.springframework.stereotype.Component;
import java.time.*;
import java.util.Set;

/** ANBIMA national banking holidays. Start inclusive, end exclusive. */
@Component
public class FinancialCalendar {
    private static final Set<MonthDay> FIXED = Set.of(MonthDay.of(1,1), MonthDay.of(4,21), MonthDay.of(5,1),
            MonthDay.of(9,7), MonthDay.of(10,12), MonthDay.of(11,2), MonthDay.of(11,15), MonthDay.of(12,25));
    public boolean businessDay(LocalDate date) {
        if (date.getYear()<2001 || date.getYear()>2099) throw new ValuationUnavailableException("Calendário fora do intervalo suportado");
        if (date.getDayOfWeek()==DayOfWeek.SATURDAY || date.getDayOfWeek()==DayOfWeek.SUNDAY || FIXED.contains(MonthDay.from(date))) return false;
        if (date.getYear()>=2024 && MonthDay.from(date).equals(MonthDay.of(11,20))) return false;
        LocalDate easter=easter(date.getYear());
        return !Set.of(easter.minusDays(48),easter.minusDays(47),easter.minusDays(2),easter.plusDays(60)).contains(date);
    }
    public int days(LocalDate start, LocalDate end) {
        return Math.toIntExact(start.datesUntil(end).filter(this::businessDay).count());
    }
    public LocalDate previousOrSame(LocalDate date) {
        while (!businessDay(date)) date=date.minusDays(1);
        return date;
    }
    private LocalDate easter(int year) {
        int a=year%19,b=year/100,c=year%100,d=b/4,e=b%4,f=(b+8)/25,g=(b-f+1)/3;
        int h=(19*a+b-d-g+15)%30,i=c/4,k=c%4,l=(32+2*e+2*i-h-k)%7,m=(a+11*h+22*l)/451;
        int n=h+l-7*m+114;
        return LocalDate.of(year,n/31,n%31+1);
    }
}
