package com.investmanager.api.valuation;

import com.investmanager.api.asset.*;
import com.investmanager.api.asset.repository.AssetRepository;
import com.investmanager.api.position.dto.PositionResponse;
import com.investmanager.api.position.service.PositionService;
import com.investmanager.api.quote.service.QuoteService;
import com.investmanager.api.transaction.*;
import com.investmanager.api.transaction.repository.TransactionRepository;
import com.investmanager.api.income.*;
import com.investmanager.api.income.repository.IncomeRepository;
import com.investmanager.api.shared.integration.ExternalIntegrationException;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import java.math.*;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static com.investmanager.api.valuation.ValuationTerms.*;

class PositionValuationServiceTest {
    final PositionService positions=mock(PositionService.class);
    final AssetRepository assets=mock(AssetRepository.class);
    final QuoteService stocks=mock(QuoteService.class);
    final CryptoQuoteService crypto=mock(CryptoQuoteService.class);
    final TreasuryQuoteProvider treasury=mock(TreasuryQuoteProvider.class);
    final ValuationContractService contracts=mock(ValuationContractService.class);
    final TransactionRepository transactions=mock(TransactionRepository.class);
    final IncomeRepository incomes=mock(IncomeRepository.class);
    final FixedIncomeCalculator fixed=new FixedIncomeCalculator(mock(IndexRateService.class),new FinancialCalendar());
    final LocalDate today=LocalDate.of(2026,11,8);
    final Clock clock=Clock.fixed(Instant.parse("2026-11-08T12:00:00Z"),ZoneId.of("America/Sao_Paulo"));
    final PositionValuationService service=new PositionValuationService(positions,assets,stocks,crypto,treasury,contracts,fixed,transactions,incomes,clock);
    Asset asset(AssetType type,String ticker,long id) {
        Asset a=new Asset();a.setType(type);a.setTicker(ticker);when(assets.findById(id)).thenReturn(Optional.of(a));return a;
    }
    PositionResponse position(long id,String ticker,String quantity,String cost) {
        return new PositionResponse(id,ticker,new BigDecimal(quantity),BigDecimal.TEN,new BigDecimal(cost));
    }
    @Test void failureInOnePositionPreservesOtherPricesAndAllCosts() {
        asset(AssetType.STOCK,"TEST11",1);asset(AssetType.CRYPTO,"BTC",2);
        when(positions.calculatePositions(1L,7L)).thenReturn(List.of(position(1,"TEST11","10","100"),position(2,"BTC","0.00000123","30")));
        when(stocks.getCurrentPrice("TEST11")).thenReturn(new BigDecimal("12"));
        when(crypto.quote("BTC")).thenThrow(new ExternalIntegrationException(503,"test"));
        var result=service.evaluate(1L,7L);
        assertThat(result.get(0).currentValue()).isEqualByComparingTo("120");
        assertThat(result.get(1).currentValue()).isNull();assertThat(result.get(1).totalCost()).isEqualByComparingTo("30");
        assertThat(result.get(1).quantity()).isEqualByComparingTo("0.00000123");
        verifyNoInteractions(transactions,incomes);
    }
    @Test void noOpenPositionNeedsNoQuotes() {
        when(positions.calculatePositions(1L,7L)).thenReturn(List.of(position(1,"BTC","0","0")));
        assertThat(service.evaluate(1L,7L)).isEmpty();verifyNoInteractions(assets,crypto,treasury,stocks);
    }
    @Test void missingContractPreservesAccounting() {
        asset(AssetType.FIXED_INCOME,"CDB",1);
        when(positions.calculatePositions(1L,7L)).thenReturn(List.of(position(1,"CDB","10","100")));
        var result=service.evaluate(1L,7L).getFirst();assertThat(result.currentValue()).isNull();assertThat(result.reason()).contains("contratuais");
        assertThat(result.totalCost()).isEqualByComparingTo("100");
    }
    ValuationTerms fixedTerms() {
        return new ValuationTerms(Instrument.CDB,RateType.PRE_FIXED,BigDecimal.TEN,null,DayCount.ACT_365,today.minusDays(365),today.plusYears(1),null,null,true,true);
    }
    Asset setupFixed() {
        Asset a=asset(AssetType.FIXED_INCOME,"CDB",1);
        when(positions.calculatePositions(1L,7L)).thenReturn(List.of(position(1,"CDB","40","400")));
        when(contracts.find(1L,1L)).thenReturn(fixedTerms());
        var buy=new Transaction(null,a,TransactionType.BUY,new BigDecimal("100"),BigDecimal.TEN,today.minusDays(365));
        var sell=new Transaction(null,a,TransactionType.SELL,new BigDecimal("60"),new BigDecimal("11"),today.minusDays(10));
        when(transactions.findByPortfolioIdAndAssetIdOrderByTransactionDateAscIdAsc(1L,1L)).thenReturn(List.of(buy,sell));
        return a;
    }
    @Test void partialRedemptionValuesRemainingPrincipalWithoutChangingAccounting() {
        setupFixed();var result=service.evaluate(1L,7L).getFirst();
        assertThat(result.currentValue()).isEqualByComparingTo("440");assertThat(result.totalCost()).isEqualByComparingTo("400");
        assertThat(result.profitLoss()).isEqualByComparingTo("40");assertThat(result.netValue()).isEqualByComparingTo("433");
        assertThat(result.valuationStatus()).isEqualTo("ESTIMATED");
        verify(transactions,never()).save(any());
    }
    @Test void paidIncomePreventsDoubleCountingBulletInterest() {
        setupFixed();when(incomes.findByPortfolioIdAndAssetIdAndBaseDateGreaterThanEqualOrderByBaseDateAscIdAsc(anyLong(),anyLong(),any()))
                .thenReturn(List.of(new Income()));
        assertThat(service.evaluate(1L,7L).getFirst().reason()).contains("dupla contagem");
    }
    @Test void mixedApplicationDatesNeedSeparateContracts() {
        Asset a=setupFixed();
        when(transactions.findByPortfolioIdAndAssetIdOrderByTransactionDateAscIdAsc(1L,1L)).thenReturn(List.of(
                new Transaction(null,a,TransactionType.BUY,new BigDecimal("40"),BigDecimal.TEN,today.minusDays(200))));
        assertThat(service.evaluate(1L,7L).getFirst().reason()).contains("datas distintas");
    }
    @ParameterizedTest @EnumSource(value=Instrument.class,names={"TESOURO_SELIC","TESOURO_PREFIXADO","TESOURO_IPCA","TESOURO_IPCA_JUROS","TESOURO_PREFIXADO_JUROS","TESOURO_RENDA"})
    void treasuryMarketPriceDoesNotAddCouponIncomeOrFuturePayments(Instrument instrument) {
        asset(AssetType.TREASURE,"TD",1);when(positions.calculatePositions(1L,7L)).thenReturn(List.of(position(1,"TD","0.12","100")));
        LocalDate conversion=today.plusYears(4),due=instrument==Instrument.TESOURO_RENDA?conversion.plusMonths(239):today.plusYears(5);
        when(contracts.find(1L,1L)).thenReturn(new ValuationTerms(instrument,null,null,null,null,null,due,null,conversion,null,true));
        when(treasury.quote(instrument,due,today)).thenReturn(new MarketQuote(new BigDecimal("1500"),"BRL",clock.instant(),"Official"));
        var result=service.evaluate(1L,7L).getFirst();
        assertThat(result.currentValue()).isEqualByComparingTo("180");assertThat(result.profitLoss()).isEqualByComparingTo("80");
        assertThat(result.valuationMethod()).isEqualTo("MARKET");verifyNoInteractions(incomes);
    }
    @Test void rendaPaymentPhaseIsExplicitlyUnavailable() {
        asset(AssetType.TREASURE,"RENDA",1);when(positions.calculatePositions(1L,7L)).thenReturn(List.of(position(1,"RENDA","1","1000")));
        when(contracts.find(1L,1L)).thenReturn(new ValuationTerms(Instrument.TESOURO_RENDA,null,null,null,null,null,today.plusMonths(239),null,today,null,true));
        assertThat(service.evaluate(1L,7L).getFirst().reason()).contains("amortizações");verifyNoInteractions(treasury);
    }
    @Test void maturedTreasuryRequiresPaymentReconciliation() {
        asset(AssetType.TREASURE,"TD",1);when(positions.calculatePositions(1L,7L)).thenReturn(List.of(position(1,"TD","1","1000")));
        when(contracts.find(1L,1L)).thenReturn(new ValuationTerms(Instrument.TESOURO_SELIC,null,null,null,null,null,today,null,null,null,true));
        assertThat(service.evaluate(1L,7L).getFirst().reason()).contains("vencido");verifyNoInteractions(treasury);
    }
}
