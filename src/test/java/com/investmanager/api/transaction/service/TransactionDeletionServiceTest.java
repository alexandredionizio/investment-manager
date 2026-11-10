package com.investmanager.api.transaction.service;

import com.investmanager.api.asset.Asset;
import com.investmanager.api.income.Income;
import com.investmanager.api.transaction.Transaction;
import com.investmanager.api.transaction.TransactionType;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TransactionDeletionServiceTest {
    private static Transaction row(long id, TransactionType type, int quantity, int day) {
        Transaction transaction = mock(Transaction.class);
        when(transaction.getId()).thenReturn(id);
        when(transaction.getType()).thenReturn(type);
        when(transaction.getQuantity()).thenReturn(BigDecimal.valueOf(quantity));
        when(transaction.getTransactionDate()).thenReturn(LocalDate.of(2026, 11, day));
        return transaction;
    }

    @Test void deletingAnOldPurchaseCannotBreakALaterSale() {
        var history = List.of(row(1, TransactionType.BUY, 10, 1), row(2, TransactionType.SELL, 8, 3));
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> TransactionDeletionService.validateRemainingHistory(history, List.of(), 1L));
        assertEquals(409, error.getStatusCode().value());
    }

    @Test void deletingAnOldSaleIsValidWhenLaterHistoryIsConsistent() {
        var history = List.of(row(1, TransactionType.BUY, 10, 1), row(2, TransactionType.SELL, 8, 3));
        assertDoesNotThrow(() -> TransactionDeletionService.validateRemainingHistory(history, List.of(), 2L));
    }

    @Test void deletingAPurchaseCannotAlterIncomeBaseDateQuantity() {
        var history = List.of(row(1, TransactionType.BUY, 10, 1), row(2, TransactionType.BUY, 2, 4));
        Income income = mock(Income.class);
        when(income.getBaseDate()).thenReturn(LocalDate.of(2026, 11, 3));
        when(income.getQuantity()).thenReturn(BigDecimal.TEN);
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> TransactionDeletionService.validateRemainingHistory(history, List.of(income), 1L));
        assertEquals(409, error.getStatusCode().value());
    }

    @Test void deletionCanProceedWhenNoIncomeOrSaleIsAffected() {
        var history = List.of(row(1, TransactionType.BUY, 10, 1), row(2, TransactionType.BUY, 5, 4));
        assertDoesNotThrow(() -> TransactionDeletionService.validateRemainingHistory(history, List.of(), 2L));
    }
}
