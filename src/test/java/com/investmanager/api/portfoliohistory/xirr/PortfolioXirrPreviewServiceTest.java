package com.investmanager.api.portfoliohistory.xirr;

import com.investmanager.api.income.repository.IncomeRepository;
import com.investmanager.api.portfolio.Portfolio;
import com.investmanager.api.portfolio.repository.PortfolioRepository;
import com.investmanager.api.portfoliohistory.capital.CapitalMovement;
import com.investmanager.api.portfoliohistory.capital.CapitalMovementRepository;
import com.investmanager.api.portfoliohistory.cash.*;
import com.investmanager.api.transaction.repository.TransactionRepository;
import com.investmanager.api.valuation.PositionValuationResponse;
import com.investmanager.api.valuation.PositionValuationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PortfolioXirrPreviewServiceTest {
    private PortfolioRepository portfolios;
    private CashOpeningCheckpointRepository checkpoints;
    private CapitalMovementRepository capital;
    private TransactionRepository transactions;
    private IncomeRepository incomes;
    private CashReconciliationService cash;
    private PositionValuationService valuations;
    private PortfolioXirrPreviewService service;
    private LocalDate start;

    @BeforeEach
    void setup() {
        portfolios = mock(PortfolioRepository.class);
        checkpoints = mock(CashOpeningCheckpointRepository.class);
        capital = mock(CapitalMovementRepository.class);
        transactions = mock(TransactionRepository.class);
        incomes = mock(IncomeRepository.class);
        cash = mock(CashReconciliationService.class);
        valuations = mock(PositionValuationService.class);
        start = LocalDate.now().minusYears(1);
        when(portfolios.findByIdAndUserId(17L, 9L)).thenReturn(Optional.of(mock(Portfolio.class)));
        service = new PortfolioXirrPreviewService(portfolios, checkpoints, capital, transactions, incomes, cash, valuations);
    }

    private void opening(BigDecimal balance) {
        CashOpeningCheckpoint cp = mock(CashOpeningCheckpoint.class);
        when(cp.getCheckpointDate()).thenReturn(start);
        when(cp.getBalance()).thenReturn(balance);
        when(checkpoints.findByPortfolio_Id(17L)).thenReturn(Optional.of(cp));
    }

    private void emptyHistory() {
        when(transactions.findByPortfolioIdOrderByTransactionDateAscIdAsc(17L)).thenReturn(List.of());
        when(incomes.findByPortfolioIdOrderByPaymentDateAscIdAsc(17L)).thenReturn(List.of());
    }

    @Test void missingOpeningCannotAssumeZero() {
        var result = service.preview(17L, 9L);
        assertEquals("OPENING_BALANCE_UNKNOWN", result.status());
        assertNull(result.annualRate());
        verifyNoInteractions(capital, valuations);
    }

    @Test void nonzeroOpeningMustNotBeMisreportedAsContribution() {
        opening(new BigDecimal("100.00"));
        var result = service.preview(17L, 9L);
        assertEquals("OPENING_NOT_ZERO", result.status());
        assertNull(result.annualRate());
        verifyNoInteractions(valuations);
    }

    @Test void noExternalTransfersMeansNoXirrEvenWithProventos() {
        opening(BigDecimal.ZERO);
        emptyHistory();
        when(capital.findByPortfolio_IdOrderByMovementDateAscIdAsc(17L)).thenReturn(List.of());
        when(cash.reconcile(eq(17L), eq(9L), any(LocalDate.class))).thenReturn(
                new CashReconciliationResponse("PROVISIONAL", LocalDate.now(), start,
                        BigDecimal.ZERO, new BigDecimal("109.17"), null, 2, "provisional"));
        when(valuations.evaluate(17L, 9L)).thenReturn(List.of());
        var result = service.preview(17L, 9L);
        assertEquals("INSUFFICIENT_OR_AMBIGUOUS_FLOWS", result.status());
        assertNull(result.annualRate());
        assertEquals(0, result.externalMovements());
    }

    @Test void negativeCashCannotProduceXirr() {
        opening(BigDecimal.ZERO);
        emptyHistory();
        when(capital.findByPortfolio_IdOrderByMovementDateAscIdAsc(17L)).thenReturn(List.of());
        when(cash.reconcile(eq(17L), eq(9L), any(LocalDate.class))).thenReturn(
                new CashReconciliationResponse("NEGATIVE_CASH", LocalDate.now(), start,
                        BigDecimal.ZERO, null, LocalDate.now(), 1, "negative"));
        var result = service.preview(17L, 9L);
        assertEquals("CASH_NOT_RECONCILED", result.status());
        assertNull(result.annualRate());
        verifyNoInteractions(valuations);
    }

    @Test void completeSyntheticFlowsYieldOnlyProvisionalRate() {
        opening(BigDecimal.ZERO);
        emptyHistory();
        CapitalMovement m = mock(CapitalMovement.class);
        when(m.getKind()).thenReturn(CapitalMovement.Kind.CONTRIBUTION);
        when(m.getMovementDate()).thenReturn(start.plusDays(1));
        when(m.getAmount()).thenReturn(new BigDecimal("100.00"));
        when(capital.findByPortfolio_IdOrderByMovementDateAscIdAsc(17L)).thenReturn(List.of(m));
        when(cash.reconcile(eq(17L), eq(9L), any(LocalDate.class))).thenReturn(
                new CashReconciliationResponse("PROVISIONAL", LocalDate.now(), start,
                        BigDecimal.ZERO, new BigDecimal("110.00"), null, 1, "provisional"));
        when(valuations.evaluate(17L, 9L)).thenReturn(List.of());
        var result = service.preview(17L, 9L);
        assertEquals("PROVISIONAL_XIRR", result.status());
        assertNotNull(result.annualRate());
        assertTrue(result.annualRate().compareTo(BigDecimal.ZERO) > 0);
        assertEquals(new BigDecimal("110.00"), result.terminalEquity());
        assertEquals(1, result.externalMovements());
    }

    @Test void anyUnvaluedPositionBlocksRate() {
        opening(BigDecimal.ZERO);
        emptyHistory();
        when(capital.findByPortfolio_IdOrderByMovementDateAscIdAsc(17L)).thenReturn(List.of());
        when(cash.reconcile(eq(17L), eq(9L), any(LocalDate.class))).thenReturn(
                new CashReconciliationResponse("PROVISIONAL", LocalDate.now(), start,
                        BigDecimal.ZERO, BigDecimal.ZERO, null, 0, "provisional"));
        PositionValuationResponse p = mock(PositionValuationResponse.class);
        when(p.valuationStatus()).thenReturn("UNAVAILABLE");
        when(valuations.evaluate(17L, 9L)).thenReturn(List.of(p));
        var result = service.preview(17L, 9L);
        assertEquals("VALUATION_INCOMPLETE", result.status());
        assertNull(result.annualRate());
    }
}
