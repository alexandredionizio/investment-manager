package com.investmanager.api.portfoliohistory.cash;

import com.investmanager.api.portfolio.exception.PortfolioNotFoundException;
import com.investmanager.api.portfolio.repository.PortfolioRepository;
import com.investmanager.api.portfoliohistory.capital.CapitalMovement;
import com.investmanager.api.portfoliohistory.capital.CapitalMovementRepository;
import com.investmanager.api.transaction.Transaction;
import com.investmanager.api.transaction.TransactionType;
import com.investmanager.api.transaction.repository.TransactionRepository;
import com.investmanager.api.income.Income;
import com.investmanager.api.income.repository.IncomeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Read-only reconciliation of persisted operations against an explicitly declared cash checkpoint. */
@Service
public class CashReconciliationService {
    private final PortfolioRepository portfolios;
    private final CashOpeningCheckpointRepository checkpoints;
    private final CapitalMovementRepository capital;
    private final TransactionRepository transactions;
    private final IncomeRepository incomes;
    private final CashLedgerCalculator calculator = new CashLedgerCalculator();

    public CashReconciliationService(PortfolioRepository portfolios, CashOpeningCheckpointRepository checkpoints,
            CapitalMovementRepository capital, TransactionRepository transactions, IncomeRepository incomes) {
        this.portfolios = portfolios;
        this.checkpoints = checkpoints;
        this.capital = capital;
        this.transactions = transactions;
        this.incomes = incomes;
    }

    @Transactional
    public CashReconciliationResponse createCheckpoint(Long portfolioId, Long userId, CashCheckpointRequest request) {
        var portfolio = portfolios.findByIdAndUserId(portfolioId, userId)
                .orElseThrow(() -> new PortfolioNotFoundException(portfolioId));
        if (request == null || !request.confirmedEndOfDayBalance() || request.date() == null ||
                request.date().isAfter(LocalDate.now()) || request.balance() == null ||
                request.balance().signum() < 0 || request.balance().scale() > 2 ||
                request.balance().precision() - request.balance().scale() > 17 ||
                request.sourceDescription() == null || request.sourceDescription().isBlank() ||
                request.sourceDescription().length() > 255) {
            throw new IllegalArgumentException("Saldo de abertura declarado inválido");
        }
        if (checkpoints.findByPortfolio_Id(portfolioId).isPresent()) {
            throw new IllegalArgumentException("Já existe saldo de abertura declarado para esta carteira; não substitua sem auditoria");
        }
        checkpoints.saveAndFlush(new CashOpeningCheckpoint(portfolio, request.date(),
                request.balance().setScale(2), request.sourceDescription().trim()));
        return reconcile(portfolioId, userId, LocalDate.now());
    }

    @Transactional(readOnly = true)
    public CashReconciliationResponse reconcile(Long portfolioId, Long userId, LocalDate asOf) {
        portfolios.findByIdAndUserId(portfolioId, userId)
                .orElseThrow(() -> new PortfolioNotFoundException(portfolioId));
        if (asOf == null || asOf.isAfter(LocalDate.now()))
            throw new IllegalArgumentException("Data de conciliação inválida");
        Optional<CashOpeningCheckpoint> opening = checkpoints.findByPortfolio_Id(portfolioId);
        if (opening.isEmpty()) {
            return new CashReconciliationResponse("OPENING_BALANCE_UNKNOWN", asOf, null, null, null, null, 0,
                    "Sem saldo de abertura declarado: caixa e XIRR indisponíveis; não presumir saldo zero");
        }
        CashOpeningCheckpoint checkpoint = opening.get();
        if (asOf.isBefore(checkpoint.getCheckpointDate())) {
            return new CashReconciliationResponse("PERIOD_BEFORE_CHECKPOINT", asOf, checkpoint.getCheckpointDate(),
                    checkpoint.getBalance(), null, null, 0,
                    "Não existe saldo inicial confiável para esta data");
        }
        LocalDate openingDate = checkpoint.getCheckpointDate();
        List<CashLedgerCalculator.Entry> entries = new ArrayList<>();
        for (CapitalMovement m : capital.findByPortfolio_IdOrderByMovementDateAscIdAsc(portfolioId)) {
            if (included(m.getMovementDate(), openingDate, asOf))
                entries.add(new CashLedgerCalculator.Entry(m.getMovementDate(),
                        m.getKind() == CapitalMovement.Kind.CONTRIBUTION ? CashLedgerCalculator.Kind.CONTRIBUTION
                                : CashLedgerCalculator.Kind.WITHDRAWAL, m.getAmount()));
        }
        for (Transaction t : transactions.findByPortfolioIdOrderByTransactionDateAscIdAsc(portfolioId)) {
            if (included(t.getTransactionDate(), openingDate, asOf)) {
                BigDecimal gross = t.getQuantity().multiply(t.getUnitPrice());
                if (gross.signum() > 0)
                    entries.add(new CashLedgerCalculator.Entry(t.getTransactionDate(),
                            t.getType() == TransactionType.BUY ? CashLedgerCalculator.Kind.BUY
                                    : CashLedgerCalculator.Kind.SELL, gross));
            }
        }
        for (Income i : incomes.findByPortfolioIdOrderByPaymentDateAscIdAsc(portfolioId)) {
            if (included(i.getPaymentDate(), openingDate, asOf) && i.getTotalAmount().signum() > 0)
                entries.add(new CashLedgerCalculator.Entry(i.getPaymentDate(),
                        CashLedgerCalculator.Kind.RECEIVED_INCOME, i.getTotalAmount()));
        }
        var result = calculator.calculate(Optional.of(checkpoint.getBalance()), openingDate, asOf, entries);
        String status = result.status() == CashLedgerCalculator.Status.NEGATIVE_CASH
                ? "NEGATIVE_CASH" : "PROVISIONAL";
        String notice = status.equals("NEGATIVE_CASH")
                ? "Histórico produz caixa negativo; verificar transferências, custos e eventos ausentes"
                : "Conciliação PROVISÓRIA: abertura declarada, sem verificação externa; valores brutos de negociação "
                  + "podem não incluir taxas, impostos, amortizações ou eventos não importados. Não usar para XIRR ainda";
        return new CashReconciliationResponse(status, asOf, openingDate, checkpoint.getBalance(),
                result.balance().orElse(null), result.firstDeficitDate(), entries.size(), notice);
    }

    private static boolean included(LocalDate date, LocalDate checkpoint, LocalDate end) {
        // Checkpoint is the end-of-day balance; its date MUST NOT be counted again.
        return date != null && date.isAfter(checkpoint) && !date.isAfter(end);
    }
}
