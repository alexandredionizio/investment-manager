package com.investmanager.api.valuation;

import com.investmanager.api.shared.integration.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.web.client.RestClient;
import org.springframework.test.web.client.MockRestServiceServer;
import java.time.*;
import static com.investmanager.api.valuation.ValuationTerms.Instrument;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;
import static org.springframework.http.MediaType.TEXT_PLAIN;

class TreasuryQuoteTest {
    final LocalDate today=LocalDate.of(2026,11,8),maturity=LocalDate.of(2035,5,15);
    final MarketDataCache cache=mock(MarketDataCache.class);
    final ExternalCalls calls=new ExternalCalls(new IntegrationProperties());
    final RestClient.Builder builder=RestClient.builder();
    final MockRestServiceServer server=MockRestServiceServer.bindTo(builder).build();
    final TreasuryCsvQuoteProvider provider=new TreasuryCsvQuoteProvider(builder.build(),calls,cache,new FinancialCalendar(),"https://treasury.test/prices.csv");
    @AfterEach void close(){calls.close();}
    String csv(String name,String date,String price) {
        return "Tipo Titulo;Data Vencimento;Data Base;PU Compra Manha;PU Venda Manha;PU Base Manha\n"
            +name+";15/05/2035;"+date+";9999,00;8888,00;"+price+"\n";
    }
    @ParameterizedTest @CsvSource({"TESOURO_SELIC,Tesouro Selic","TESOURO_PREFIXADO,Tesouro Prefixado",
        "TESOURO_PREFIXADO_JUROS,Tesouro Prefixado com Juros Semestrais","TESOURO_IPCA,Tesouro IPCA+",
        "TESOURO_IPCA_JUROS,Tesouro IPCA+ com Juros Semestrais","TESOURO_RENDA,Tesouro Renda+ Aposentadoria Extra"})
    void exactTitleAndMaturityUseOfficialD0BasePrice(Instrument instrument,String name) {
        server.expect(requestTo("https://treasury.test/prices.csv")).andRespond(withSuccess(csv(name,"06/11/2026","1.234,567890"),TEXT_PLAIN));
        var quote=provider.quote(instrument,maturity,today);
        assertThat(quote.price()).isEqualByComparingTo("1234.567890");
        assertThat(quote.referenceTime()).isEqualTo(Instant.parse("2026-11-06T03:00:00Z"));
        assertThat(quote.source()).contains("D0");server.verify();
    }
    @Test void freshCacheAvoidsExternalCall() {
        when(cache.get(anyString())).thenReturn(csv("Tesouro Selic","06/11/2026","100,00"));
        assertThat(provider.quote(Instrument.TESOURO_SELIC,maturity,today).price()).isEqualByComparingTo("100");server.verify();
    }
    @ParameterizedTest @ValueSource(strings={"03/11/2026","09/11/2026"})
    void staleAndFuturePricesCannotValuePosition(String date) {
        when(cache.get(anyString())).thenReturn(csv("Tesouro Selic",date,"100,00"));
        assertThatThrownBy(()->provider.quote(Instrument.TESOURO_SELIC,maturity,today)).isInstanceOf(ValuationUnavailableException.class);
    }
    @Test void wrongMaturityCannotBeSubstituted() {
        when(cache.get(anyString())).thenReturn(csv("Tesouro Selic","06/11/2026","100,00"));
        assertThatThrownBy(()->provider.quote(Instrument.TESOURO_SELIC,maturity.plusYears(1),today)).isInstanceOf(ValuationUnavailableException.class);
    }
    @Test void malformedResponseCannotBecomeValuation() {
        server.expect(anything()).andRespond(withSuccess("<html>error</html>",TEXT_PLAIN));
        assertThatThrownBy(()->provider.quote(Instrument.TESOURO_SELIC,maturity,today)).isInstanceOf(ExternalIntegrationException.class);
        verify(cache,never()).put(anyString(),anyString(),any());
    }
    @Test void newestDateIsSelectedRegardlessOfCsvOrder() {
        String csv=csv("Tesouro Selic","06/11/2026","100,00")+"Tesouro Selic;15/05/2035;05/11/2026;9,00;8,00;90,00\n";
        when(cache.get(anyString())).thenReturn(csv);
        assertThat(provider.quote(Instrument.TESOURO_SELIC,maturity,today).price()).isEqualByComparingTo("100");
    }
}
