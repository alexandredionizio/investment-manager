package com.investmanager.api.portfoliohistory.cash;

import com.investmanager.api.portfolio.Portfolio;
import com.investmanager.api.portfolio.repository.PortfolioRepository;
import com.investmanager.api.portfoliohistory.capital.CapitalMovement;
import com.investmanager.api.portfoliohistory.capital.CapitalMovementRepository;
import com.investmanager.api.transaction.Transaction;
import com.investmanager.api.transaction.TransactionType;
import com.investmanager.api.transaction.repository.TransactionRepository;
import com.investmanager.api.income.Income;
import com.investmanager.api.income.IncomeType;
import com.investmanager.api.income.repository.IncomeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CashReconciliationServiceTest {
    PortfolioRepository portfolios = mock(PortfolioRepository.class);
    CashOpeningCheckpointRepository checkpoints = mock(CashOpeningCheckpointRepository.class);
    CapitalMovementRepository capital = mock(CapitalMovementRepository.class);
    TransactionRepository transactions = mock(TransactionRepository.class);
    IncomeRepository incomes = mock(IncomeRepository.class);
    CashReconciliationService service = new CashReconciliationService(portfolios, checkpoints, capital, transactions, incomes);
    Portfolio portfolio = new Portfolio();
    LocalDate checkpointDate = LocalDate.of(2026, 10, 1);
    @BeforeEach void setup() {
        when(portfolios.findByIdAndUserId(7L, 2L)).thenReturn(Optional.of(portfolio));
        when(capital.findByPortfolio_IdOrderByMovementDateAscIdAsc(7L)).thenReturn(List.of());
        when(transactions.findByPortfolioIdOrderByTransactionDateAscIdAsc(7L)).thenReturn(List.of());
        when(incomes.findByPortfolioIdOrderByPaymentDateAscIdAsc(7L)).thenReturn(List.of());
    }
    void opening(BigDecimal balance) {
        when(checkpoints.findByPortfolio_Id(7L)).thenReturn(Optional.of(
                new CashOpeningCheckpoint(portfolio, checkpointDate, balance, "Extrato confirmado pelo titular")));
    }
    @Test void unknownOpeningNeverProducesZero() {
        var result = service.reconcile(7L, 2L, checkpointDate.plusDays(1));
        assertEquals("OPENING_BALANCE_UNKNOWN", result.status());
        assertNull(result.computedCashBalance());
        verifyNoInteractions(transactions, incomes, capital);
    }
    @Test void rejectsOtherUsers() {
        assertThrows(RuntimeException.class, () -> service.reconcile(7L, 99L, checkpointDate.plusDays(1)));
    }
    @Test void rejectsDatesBeforeCheckpoint() {
        opening(new BigDecimal("100"));
        var result = service.reconcile(7L, 2L, checkpointDate.minusDays(1));
        assertEquals("PERIOD_BEFORE_CHECKPOINT", result.status());
        assertNull(result.computedCashBalance());
    }
    @Test void ignoresOperationsOnCheckpointDateAndFuture() {
        opening(new BigDecimal("100"));
        when(capital.findByPortfolio_IdOrderByMovementDateAscIdAsc(7L)).thenReturn(List.of(
                new CapitalMovement(portfolio, UUID.randomUUID(), CapitalMovement.Kind.CONTRIBUTION,
                        checkpointDate, new BigDecimal("300"), null),
                new CapitalMovement(portfolio, UUID.randomUUID(), CapitalMovement.Kind.CONTRIBUTION,
                        checkpointDate.plusDays(2), new BigDecimal("900"), null)));
        var result = service.reconcile(7L, 2L, checkpointDate.plusDays(1));
        assertEquals("PROVISIONAL", result.status());
        assertEquals(0, result.computedCashBalance().compareTo(new BigDecimal("100")));
    }
    @Test void combinesMovementsTradesAndOnlyPaidIncomes() {
        opening(new BigDecimal("100.00"));
        LocalDate day = checkpointDate.plusDays(1);
        when(capital.findByPortfolio_IdOrderByMovementDateAscIdAsc(7L)).thenReturn(List.of(
                new CapitalMovement(portfolio, UUID.randomUUID(), CapitalMovement.Kind.CONTRIBUTION, day,
                        new BigDecimal("50"), null),
                new CapitalMovement(portfolio, UUID.randomUUID(), CapitalMovement.Kind.WITHDRAWAL, day,
                        new BigDecimal("20"), null)));
        Transaction buy = new Transaction(); buy.setTransactionDate(day); buy.setType(TransactionType.BUY);
        buy.setQuantity(new BigDecimal("2")); buy.setUnitPrice(new BigDecimal("10"));
        Transaction sell = new Transaction(); sell.setTransactionDate(day); sell.setType(TransactionType.SELL);
        sell.setQuantity(new BigDecimal("1")); sell.setUnitPrice(new BigDecimal("8"));
        when(transactions.findByPortfolioIdOrderByTransactionDateAscIdAsc(7L)).thenReturn(List.of(buy, sell));
        Income paid = new Income(portfolio, null, IncomeType.DIVIDEND, new BigDecimal("3"),
                new BigDecimal("2"), day, day);
        Income future = new Income(portfolio, null, IncomeType.DIVIDEND, new BigDecimal("999"),
                BigDecimal.ONE, day, day.plusDays(30));
        when(incomes.findByPortfolioIdOrderByPaymentDateAscIdAsc(7L)).thenReturn(List.of(paid, future));
        var result = service.reconcile(7L, 2L, day);
        assertEquals("PROVISIONAL", result.status());
        assertEquals(0, result.computedCashBalance().compareTo(new BigDecimal("124.00")));
        assertEquals(5, result.movementsConsidered());
    }
    @Test void negativeHistoricalDayIsNotHiddenByLaterContribution() {
        opening(new BigDecimal("10"));
        when(capital.findByPortfolio_IdOrderByMovementDateAscIdAsc(7L)).thenReturn(List.of(
                new CapitalMovement(portfolio, UUID.randomUUID(), CapitalMovement.Kind.WITHDRAWAL,
                        checkpointDate.plusDays(1), new BigDecimal("20"), null),
                new CapitalMovement(portfolio, UUID.randomUUID(), CapitalMovement.Kind.CONTRIBUTION,
                        checkpointDate.plusDays(2), new BigDecimal("50"), null)));
        var result = service.reconcile(7L, 2L, checkpointDate.plusDays(2));
        assertEquals("NEGATIVE_CASH", result.status());
        assertEquals(checkpointDate.plusDays(1), result.firstDeficitDate());
        assertNull(result.computedCashBalance());
    }
    @Test void futureCheckpointRejected() {
        var req = new CashCheckpointRequest(LocalDate.now().plusDays(1), BigDecimal.ZERO, "Comprovante", true);
        assertThrows(IllegalArgumentException.class, () -> service.createCheckpoint(7L, 2L, req));
    }
    @Test void cannotOverwriteExistingCheckpoint() {
        opening(BigDecimal.ONE);
        var req = new CashCheckpointRequest(checkpointDate, BigDecimal.ONE, "Comprovante", true);
        assertThrows(IllegalArgumentException.class, () -> service.createCheckpoint(7L, 2L, req));
    }
}
