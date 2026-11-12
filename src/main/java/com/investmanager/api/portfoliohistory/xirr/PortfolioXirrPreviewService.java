package com.investmanager.api.portfoliohistory.xirr;

import com.investmanager.api.income.repository.IncomeRepository;
import com.investmanager.api.portfolio.exception.PortfolioNotFoundException;
import com.investmanager.api.portfolio.repository.PortfolioRepository;
import com.investmanager.api.portfoliohistory.capital.CapitalMovement;
import com.investmanager.api.portfoliohistory.capital.CapitalMovementRepository;
import com.investmanager.api.portfoliohistory.cash.CashOpeningCheckpointRepository;
import com.investmanager.api.portfoliohistory.cash.CashReconciliationService;
import com.investmanager.api.transaction.repository.TransactionRepository;
import com.investmanager.api.valuation.PositionValuationResponse;
import com.investmanager.api.valuation.PositionValuationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Read-only, owner-scoped preview, never a certified investment performance indicator.
 * Includes actual cash plus all currently available position valuations. Does not
 * misclassify buys, sells or dividends as contributions/withdrawals.
 * Since there is no independently reconciled external-transfer ledger, all computed
 * results are explicitly PROVISIONAL and must NOT be placed on the dashboard.
 */
@Service
public class PortfolioXirrPreviewService {
    private final PortfolioRepository portfolios;
    private final CashOpeningCheckpointRepository checkpoints;
    private final CapitalMovementRepository capital;
    private final TransactionRepository transactions;
    private final IncomeRepository incomes;
    private final CashReconciliationService cash;
    private final PositionValuationService valuations;
    private final PortfolioXirrEvaluator evaluator = new PortfolioXirrEvaluator();

    public PortfolioXirrPreviewService(PortfolioRepository portfolios, CashOpeningCheckpointRepository checkpoints,
            CapitalMovementRepository capital, TransactionRepository transactions,
            IncomeRepository incomes, CashReconciliationService cash, PositionValuationService valuations) {
        this.portfolios = portfolios;
        this.checkpoints = checkpoints;
        this.capital = capital;
        this.transactions = transactions;
        this.incomes = incomes;
        this.cash = cash;
        this.valuations = valuations;
    }

    @Transactional(readOnly = true)
    public PortfolioXirrPreviewResponse preview(Long portfolioId, Long userId) {
        portfolios.findByIdAndUserId(portfolioId, userId)
                .orElseThrow(() -> new PortfolioNotFoundException(portfolioId));
        LocalDate today = LocalDate.now();
        var opening = checkpoints.findByPortfolio_Id(portfolioId);
        if (opening.isEmpty()) return unavailable("OPENING_BALANCE_UNKNOWN", today,
                "Sem abertura de caixa declarada; não é permitido presumir zero");
        var checkpoint = opening.get();
        LocalDate start = checkpoint.getCheckpointDate();
        if (start.isAfter(today) || checkpoint.getBalance().signum() != 0)
            return unavailable("OPENING_NOT_ZERO", today,
                    "A prévia exige início com caixa zero; capital anterior não foi reconstruído");

        var allTransfers = capital.findByPortfolio_IdOrderByMovementDateAscIdAsc(portfolioId);
        var allTrades = transactions.findByPortfolioIdOrderByTransactionDateAscIdAsc(portfolioId);
        var allIncomes = incomes.findByPortfolioIdOrderByPaymentDateAscIdAsc(portfolioId);
        // Opening is an end-of-day checkpoint. No operation on/before that date
        // can be silently ignored, including nonzero positions already held.
        if (allTransfers.stream().anyMatch(t -> !t.getMovementDate().isAfter(start))
                || allTrades.stream().anyMatch(t -> !t.getTransactionDate().isAfter(start))
                || allIncomes.stream().anyMatch(i -> !i.getPaymentDate().isAfter(start)))
            return unavailable("PREEXISTING_HISTORY", today,
                    "Há eventos no dia da abertura ou antes; faltam fluxos e posições anteriores");
        if (allTrades.stream().anyMatch(t -> t.getTransactionDate().isAfter(today))
                || allIncomes.stream().anyMatch(i -> i.getPaymentDate().isAfter(today))
                || allTransfers.stream().anyMatch(t -> t.getMovementDate().isAfter(today)))
            return unavailable("FUTURE_MOVEMENTS", today,
                    "Movimentações futuras exigem tratamento separado antes da prévia");

        var reconciled = cash.reconcile(portfolioId, userId, today);
        if (!"PROVISIONAL".equals(reconciled.status()) || reconciled.computedCashBalance() == null)
            return unavailable("CASH_NOT_RECONCILED", today,
                    "Caixa indisponível, deficitário ou não conciliado; XIRR não calculada");

        List<PositionValuationResponse> positions;
        try {
            positions = valuations.evaluate(portfolioId, userId);
        } catch (RuntimeException ex) {
            return unavailable("VALUATION_UNAVAILABLE", today,
                    "A avaliação dos ativos não pôde ser obtida integralmente");
        }
        if (positions == null || positions.stream().anyMatch(p -> p == null
                || !"AVAILABLE".equals(p.valuationStatus()) || p.currentValue() == null
                || p.currentValue().signum() < 0))
            return unavailable("VALUATION_INCOMPLETE", today,
                    "Há ativos sem cotação de mercado confiável ou apenas valores estimados");
        BigDecimal positionTotal = positions.stream().map(PositionValuationResponse::currentValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal equity = reconciled.computedCashBalance().add(positionTotal);
        if (equity.signum() < 0) return unavailable("EQUITY_INVALID", today, "Patrimônio final inválido");
        List<ExternalCapitalMovement> flows = allTransfers.stream().map(m ->
                new ExternalCapitalMovement(m.getMovementDate(),
                        m.getKind() == CapitalMovement.Kind.CONTRIBUTION
                                ? ExternalCapitalMovement.Kind.CONTRIBUTION
                                : ExternalCapitalMovement.Kind.WITHDRAWAL, m.getAmount())).toList();
        var rate = evaluator.calculate(flows, today, equity, true);
        if (rate.isEmpty()) return new PortfolioXirrPreviewResponse("INSUFFICIENT_OR_AMBIGUOUS_FLOWS", today,
                null, equity, reconciled.computedCashBalance(), positionTotal, flows.size(),
                "Sem fluxo externo suficiente ou raiz anualizada única; não estimar taxa");
        return new PortfolioXirrPreviewResponse("PROVISIONAL_XIRR", today, rate.get(), equity,
                reconciled.computedCashBalance(), positionTotal, flows.size(),
                "Prévia NÃO AUDITADA. Saldos de abertura, taxas, impostos e completude dos aportes/retiradas "
                        + "não foram verificados por extrato. Não exibir no Dashboard como rentabilidade definitiva");
    }

    private static PortfolioXirrPreviewResponse unavailable(String code, LocalDate date, String reason) {
        return new PortfolioXirrPreviewResponse(code, date, null, null, null, null, 0, reason);
    }
}
