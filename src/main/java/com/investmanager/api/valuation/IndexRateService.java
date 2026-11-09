package com.investmanager.api.valuation;

import com.investmanager.api.benchmark.client.bcb.BcbClient;
import com.investmanager.api.shared.integration.*;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class IndexRateService {
    private final BcbClient bcb;
    private final MarketDataCache cache;
    private final IntegrationProperties properties;
    private static final DateTimeFormatter DATE=DateTimeFormatter.ofPattern("dd/MM/uuuu");
    public IndexRateService(BcbClient bcb, MarketDataCache cache, IntegrationProperties properties) {
        this.bcb=bcb;this.cache=cache;this.properties=properties;
    }
    public Map<LocalDate,BigDecimal> rates(int series, LocalDate start, LocalDate end) {
        if (end.isBefore(start)) return Map.of();
        Map<LocalDate,BigDecimal> result=new TreeMap<>();
        // SGS daily queries are bounded. Chunking also bounds response size and cache keys.
        for (LocalDate from=start;!from.isAfter(end);from=from.plusYears(1)) {
            LocalDate to=from.plusYears(1).minusDays(1);if(to.isAfter(end))to=end;
            String key="valuation:sgs:v1:"+series+":"+from+":"+to;
            String payload=cache.get(key);
            Map<LocalDate,BigDecimal> chunk=null;
            if(payload!=null) {
                try { chunk=decode(payload,from,to); }
                catch(RuntimeException e) { cache.invalidEntry(); }
            }
            if(chunk==null) {
                chunk=new TreeMap<>();
                for(var point:bcb.getSeries(series,from,to)) {
                    LocalDate date=LocalDate.parse(point.date(),DATE);
                    BigDecimal value=new BigDecimal(point.value());
                    if(date.isBefore(from)||date.isAfter(to)||value.compareTo(new BigDecimal("-100"))<=0||chunk.putIfAbsent(date,value)!=null)
                        throw new ValuationUnavailableException("Série de indexador inválida");
                }
                if(!chunk.isEmpty()) {
                    StringBuilder encoded=new StringBuilder();
                    chunk.forEach((date,value)->encoded.append(date).append('=').append(value.toPlainString()).append(';'));
                    cache.put(key,encoded.toString(),Duration.ofSeconds(properties.getHistoryTtlSeconds()));
                }
            }
            result.putAll(chunk);
        }
        return result;
    }
    private Map<LocalDate,BigDecimal> decode(String payload,LocalDate start,LocalDate end) {
        Map<LocalDate,BigDecimal> values=new TreeMap<>();
        for(String row:payload.split(";")) {
            String[] fields=row.split("=");LocalDate date=LocalDate.parse(fields[0]);BigDecimal value=new BigDecimal(fields[1]);
            if(date.isBefore(start)||date.isAfter(end)||value.compareTo(new BigDecimal("-100"))<=0||values.putIfAbsent(date,value)!=null)
                throw new IllegalArgumentException("Cache inválido");
        }
        return values;
    }
}
