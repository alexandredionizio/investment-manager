package com.investmanager.api.valuation;

import com.investmanager.api.shared.integration.*;
import org.springframework.beans.factory.annotation.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import static com.investmanager.api.valuation.ValuationTerms.Instrument;

@Component
public class TreasuryCsvQuoteProvider implements TreasuryQuoteProvider {
    private static final Map<Instrument,String> NAMES=Map.of(
            Instrument.TESOURO_SELIC,"Tesouro Selic", Instrument.TESOURO_PREFIXADO,"Tesouro Prefixado",
            Instrument.TESOURO_PREFIXADO_JUROS,"Tesouro Prefixado com Juros Semestrais",
            Instrument.TESOURO_IPCA,"Tesouro IPCA+", Instrument.TESOURO_IPCA_JUROS,"Tesouro IPCA+ com Juros Semestrais",
            Instrument.TESOURO_RENDA,"Tesouro RendA+ Aposentadoria Extra");
    private static final DateTimeFormatter DATE=DateTimeFormatter.ofPattern("dd/MM/uuuu").withResolverStyle(java.time.format.ResolverStyle.STRICT);
    private final RestClient client;
    private final ExternalCalls calls;
    private final MarketDataCache cache;
    private final FinancialCalendar calendar;
    private final String url;
    public TreasuryCsvQuoteProvider(@Qualifier("treasuryRestClient") RestClient client, ExternalCalls calls,
            MarketDataCache cache, FinancialCalendar calendar,
            @Value("${treasury.csv-url:https://www.tesourotransparente.gov.br/ckan/dataset/df56aa42-484a-4a59-8184-7676580c81e3/resource/796d2059-14e9-44e3-80c9-2d9e30b405c1/download/precotaxatesourodireto.csv}") String url) {
        this.client=client;this.calls=calls;this.cache=cache;this.calendar=calendar;this.url=url;
    }
    public MarketQuote quote(Instrument instrument,LocalDate maturity,LocalDate date) {
        if(!NAMES.containsKey(instrument))throw new ValuationUnavailableException("Título não suportado");
        String key="valuation:treasury:base:v1:"+date;
        String compact=cache.get(key);
        Map<String,Row> rows=null;
        if(compact!=null) {
            try { rows=parse(compact,date); }
            catch(RuntimeException e) { cache.invalidEntry(); }
        }
        if(rows==null) {
            rows=calls.get("treasury","official-base-price",()->{
                byte[] bytes=client.get().uri(url).retrieve().body(byte[].class);
                if(bytes==null)throw new IllegalStateException("Arquivo do Tesouro vazio");
                String csv=new String(bytes,StandardCharsets.UTF_8);
                if(csv.indexOf('�')>=0)csv=new String(bytes,StandardCharsets.ISO_8859_1);
                return parse(csv,date);
            });
            StringBuilder encoded=new StringBuilder("Tipo Titulo;Data Vencimento;Data Base;PU Base Manha\n");
            rows.values().forEach(row->encoded.append(row.name).append(';').append(row.maturity.format(DATE)).append(';')
                    .append(row.date.format(DATE)).append(';').append(row.price.toPlainString().replace('.',',')).append('\n'));
            cache.put(key,encoded.toString(),Duration.ofMinutes(15));
        }
        Row row=rows.get(NAMES.get(instrument)+"|"+maturity);
        if(row==null || calendar.days(row.date,date)>1 || row.price.signum()<=0)
            throw new ValuationUnavailableException("Preço oficial recente indisponível para o título e vencimento");
        return new MarketQuote(row.price,"BRL",row.date.atStartOfDay(ZoneId.of("America/Sao_Paulo")).toInstant(),"Tesouro Transparente — PU Base Manhã (D0)");
    }
    static Map<String,Row> parse(String csv,LocalDate date) {
        String[] lines=csv.replace("\r", "").replace("\uFEFF", "").split("\n");
        String[] header=lines[0].split(";",-1);
        int name=index(header,"Tipo Titulo"),maturity=index(header,"Data Vencimento"),reference=index(header,"Data Base"),price=index(header,"PU Base Manha");
        Map<String,Row> result=new HashMap<>();
        for(int i=1;i<lines.length;i++) {
            if(lines[i].isBlank())continue;
            String[] fields=lines[i].split(";",-1);
            if(fields.length!=header.length)throw new IllegalStateException("CSV do Tesouro inválido");
            String rawTitle=clean(fields[name]);
            String title=NAMES.values().stream().filter(n->n.equalsIgnoreCase(rawTitle)).findFirst().orElse(null);
            if(title==null)continue;
            LocalDate day=LocalDate.parse(clean(fields[reference]),DATE);
            if(day.isAfter(date)||day.isBefore(date.minusDays(10)))continue;
            LocalDate due=LocalDate.parse(clean(fields[maturity]),DATE);
            String value=clean(fields[price]);
            if(value.isBlank())continue;
            BigDecimal pu=new BigDecimal(value.replace(".","").replace(",","."));
            Row row=new Row(title,due,day,pu);
            String key=title+"|"+due;
            Row previous=result.get(key);
            if(previous==null||day.isAfter(previous.date))result.put(key,row);
            else if(day.equals(previous.date)&&pu.compareTo(previous.price)!=0)throw new IllegalStateException("Preços oficiais conflitantes");
        }
        return result;
    }
    private static String clean(String value) { return value.replace("\"", "").strip(); }
    private static int index(String[] header,String name) {
        for(int i=0;i<header.length;i++)if(clean(header[i]).equals(name))return i;
        throw new IllegalStateException("Coluna ausente no CSV do Tesouro: "+name);
    }
    record Row(String name,LocalDate maturity,LocalDate date,BigDecimal price) {}
}
