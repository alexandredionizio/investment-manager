package com.investmanager.api.valuation;

import com.investmanager.api.asset.*;
import com.investmanager.api.asset.repository.AssetRepository;
import com.investmanager.api.position.dto.PositionResponse;
import com.investmanager.api.position.service.PositionService;
import com.investmanager.api.quote.service.QuoteService;
import com.investmanager.api.quote.exception.*;
import com.investmanager.api.shared.integration.ExternalIntegrationException;
import com.investmanager.api.transaction.*;
import com.investmanager.api.transaction.repository.TransactionRepository;
import com.investmanager.api.income.repository.IncomeRepository;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import java.math.*;
import java.time.*;
import java.util.List;
import static com.investmanager.api.valuation.ValuationTerms.Instrument;

@Service
public class PositionValuationService {
    private final PositionService positions;
    private final AssetRepository assets;
    private final QuoteService stocks;
    private final CryptoQuoteService crypto;
    private final TreasuryQuoteProvider treasury;
    private final ValuationContractService contracts;
    private final FixedIncomeCalculator fixed;
    private final TransactionRepository transactions;
    private final IncomeRepository incomes;
    private final Clock clock;
    public PositionValuationService(PositionService positions, AssetRepository assets, QuoteService stocks,
            CryptoQuoteService crypto, TreasuryQuoteProvider treasury, ValuationContractService contracts,
            FixedIncomeCalculator fixed, TransactionRepository transactions, IncomeRepository incomes, Clock clock) {
        this.positions=positions;this.assets=assets;this.stocks=stocks;this.crypto=crypto;this.treasury=treasury;
        this.contracts=contracts;this.fixed=fixed;this.transactions=transactions;this.incomes=incomes;this.clock=clock;
    }
    public List<PositionValuationResponse> evaluate(Long portfolioId,Long userId) {
        // Authorization and stabilized accounting remain in PositionService.
        return positions.calculatePositions(portfolioId,userId).stream().filter(p->p.quantity().signum()>0)
                .map(p->evaluate(portfolioId,p)).toList();
    }
    private PositionValuationResponse evaluate(Long portfolioId,PositionResponse p) {
        Asset asset=assets.findById(p.assetId()).orElseThrow();
        try {
            return switch(asset.getType()) {
                case CRYPTO -> market(p,asset.getType(),crypto.quote(asset.getTicker()));
                case TREASURE -> treasury(portfolioId,p);
                case FIXED_INCOME -> fixed(portfolioId,p);
                default -> {
                    BigDecimal price=stocks.getCurrentPrice(asset.getTicker());
                    yield PositionValuationResponse.valued(p,asset.getType(),price,p.quantity().multiply(price),
                            "MARKET",null,"Brapi",null,null);
                }
            };
        } catch(ValuationUnavailableException | UnsupportedAssetQuoteException | QuoteNotFoundException e) {
            return PositionValuationResponse.unavailable(p,asset.getType(),e.getMessage());
        } catch(ExternalIntegrationException | RestClientException e) {
            return PositionValuationResponse.unavailable(p,asset.getType(),"Fonte de dados temporariamente indisponível");
        } catch(IllegalStateException | IllegalArgumentException e) {
            return PositionValuationResponse.unavailable(p,asset.getType(),"Dados de avaliação inválidos ou insuficientes");
        }
    }
    private PositionValuationResponse market(PositionResponse p,AssetType type,MarketQuote quote) {
        return PositionValuationResponse.valued(p,type,quote.price(),p.quantity().multiply(quote.price()),
                "MARKET",quote.referenceTime(),quote.source(),null,null);
    }
    private ValuationTerms terms(Long portfolioId,Long assetId) {
        var terms=contracts.find(portfolioId,assetId);
        if(terms==null)throw new ValuationUnavailableException("Complemente os dados contratuais para avaliar este ativo");
        return terms;
    }
    private PositionValuationResponse treasury(Long portfolioId,PositionResponse p) {
        var terms=terms(portfolioId,p.assetId());LocalDate today=LocalDate.now(clock);
        if(!terms.treasury())throw new ValuationUnavailableException("Contrato incompatível");
        if(!today.isBefore(terms.maturityDate()))throw new ValuationUnavailableException("Título vencido: concilie o pagamento e a posição");
        if(terms.instrument()==Instrument.TESOURO_RENDA && !today.isBefore(terms.conversionDate()))
            throw new ValuationUnavailableException("RendA+ em fase de pagamentos: amortizações exigem conciliação antes da avaliação");
        return market(p,AssetType.TREASURE,treasury.quote(terms.instrument(),terms.maturityDate(),today));
    }
    private PositionValuationResponse fixed(Long portfolioId,PositionResponse p) {
        var terms=terms(portfolioId,p.assetId());LocalDate today=LocalDate.now(clock);
        if(terms.treasury())throw new ValuationUnavailableException("Contrato incompatível");
        var history=transactions.findByPortfolioIdAndAssetIdOrderByTransactionDateAscIdAsc(portfolioId,p.assetId());
        var buys=history.stream().filter(t->t.getType()==TransactionType.BUY).toList();
        // One issuance/application per asset: a second lot needs its own contractual registration.
        if(buys.isEmpty() || buys.stream().anyMatch(t->!t.getTransactionDate().equals(terms.applicationDate()))
                || history.stream().anyMatch(t->t.getTransactionDate().isAfter(today)))
            throw new ValuationUnavailableException("Aplicações em datas distintas exigem contratos separados por ativo");
        if(!incomes.findByPortfolioIdAndAssetIdAndBaseDateGreaterThanEqualOrderByBaseDateAscIdAsc(
                portfolioId,p.assetId(),terms.applicationDate()).isEmpty())
            throw new ValuationUnavailableException("Contrato com pagamentos: concilie os rendimentos para evitar dupla contagem");
        BigDecimal originalQuantity=buys.stream().map(Transaction::getQuantity).reduce(BigDecimal.ZERO,BigDecimal::add);
        BigDecimal originalPrincipal=buys.stream().map(t->t.getQuantity().multiply(t.getUnitPrice())).reduce(BigDecimal.ZERO,BigDecimal::add);
        BigDecimal principal=originalPrincipal.multiply(p.quantity()).divide(originalQuantity,DecimalFactors.MC);
        BigDecimal gross=principal.multiply(fixed.factor(terms,today),DecimalFactors.MC);
        BigDecimal unit=gross.divide(p.quantity(),12,RoundingMode.HALF_UP);
        LocalDate reference=today.isAfter(terms.maturityDate())?terms.maturityDate():today;
        String note="Remuneração contratual estimada, sem preço de resgate nem taxas/custódia. Líquido simulado apenas para pessoa física residente confirmada.";
        if(!today.isBefore(terms.maturityDate()))note+=" Vencido: valor limitado ao vencimento; pagamento pendente de conciliação.";
        return PositionValuationResponse.valued(p,AssetType.FIXED_INCOME,unit,gross,"CONTRACTUAL",
                reference.atStartOfDay(clock.getZone()).toInstant(),terms.rateType()==ValuationTerms.RateType.PRE_FIXED?"Contrato":"Contrato / BCB SGS",
                note,fixed.netValue(terms,gross,principal,today));
    }
}
