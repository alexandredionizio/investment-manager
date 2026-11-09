package com.investmanager.api.income.service;

import com.investmanager.api.asset.Asset;
import com.investmanager.api.income.Income;
import com.investmanager.api.income.IncomeType;
import com.investmanager.api.income.exception.IncomeEligibilityConflictException;
import com.investmanager.api.income.repository.IncomeRepository;
import com.investmanager.api.portfolio.Portfolio;
import com.investmanager.api.transaction.Transaction;
import com.investmanager.api.transaction.TransactionType;
import com.investmanager.api.transaction.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class IncomeEligibilityValidatorTest {
    private final IncomeRepository incomes = mock(IncomeRepository.class);
    private final TransactionRepository transactions = mock(TransactionRepository.class);
    private final IncomeEligibilityValidator validator = new IncomeEligibilityValidator(incomes, transactions);
    private final Portfolio portfolio = new Portfolio();
    private final Asset asset = new Asset();
    private LocalDate date(int day) { return LocalDate.of(2026, 9, day); }
    private Transaction transaction(TransactionType type, String quantity, int day) {
        return new Transaction(portfolio, asset, type, new BigDecimal(quantity), BigDecimal.TEN, date(day));
    }
    private Income income(String quantity, int day) {
        return new Income(portfolio, asset, IncomeType.DIVIDEND, new BigDecimal("0.50"),
                new BigDecimal(quantity), date(day), date(25));
    }
    @BeforeEach void history() {
        lenient().when(transactions.findByPortfolioIdAndAssetIdOrderByTransactionDateAscIdAsc(1L, 2L))
                .thenReturn(List.of(transaction(TransactionType.BUY, "100.25", 1)));
    }
    @ParameterizedTest @CsvSource({"SELL,50,5", "BUY,50,5", "SELL,0.25,10", "BUY,0.25,10"})
    void rejectsChangedEligibilityIncludingSameDayAndFractions(TransactionType type, String quantity, int day) {
        when(incomes.findByPortfolioIdAndAssetIdAndBaseDateGreaterThanEqualOrderByBaseDateAscIdAsc(1L, 2L, date(day)))
                .thenReturn(List.of(income("100.25", 10)));
        assertThrows(IncomeEligibilityConflictException.class,
                () -> validator.validate(1L, 2L, transaction(type, quantity, day)));
        verify(incomes, never()).save(any());
    }
    @Test void ignoresUnaffectedIncomeWithoutReadingTransactionHistory() {
        validator.validate(1L, 2L, transaction(TransactionType.BUY, "50", 11));
        verify(incomes).findByPortfolioIdAndAssetIdAndBaseDateGreaterThanEqualOrderByBaseDateAscIdAsc(1L, 2L, date(11));
        verifyNoInteractions(transactions);
    }
    @Test void acceptsOnlyEffectiveAgreementWithoutRewritingExistingIncome() {
        Income original = income("100.75000000", 10);
        when(incomes.findByPortfolioIdAndAssetIdAndBaseDateGreaterThanEqualOrderByBaseDateAscIdAsc(1L, 2L, date(5)))
                .thenReturn(List.of(original));
        assertDoesNotThrow(() -> validator.validate(1L, 2L, transaction(TransactionType.BUY, "0.50", 5)));
        assertEquals(new BigDecimal("100.75000000"), original.getQuantity());
        verify(incomes, never()).save(any());
    }
    @Test void checksEveryAffectedBaseDate() {
        when(incomes.findByPortfolioIdAndAssetIdAndBaseDateGreaterThanEqualOrderByBaseDateAscIdAsc(1L, 2L, date(5)))
                .thenReturn(List.of(income("150.25", 10), income("100.25", 20)));
        assertThrows(IncomeEligibilityConflictException.class,
                () -> validator.validate(1L, 2L, transaction(TransactionType.BUY, "50", 5)));
    }
    @Test void doesNotHideRepositoryFailureAsNoIncome() {
        RuntimeException failure = new IllegalStateException("database unavailable");
        when(incomes.findByPortfolioIdAndAssetIdAndBaseDateGreaterThanEqualOrderByBaseDateAscIdAsc(anyLong(), anyLong(), any()))
                .thenThrow(failure);
        assertSame(failure, assertThrows(IllegalStateException.class,
                () -> validator.validate(1L, 2L, transaction(TransactionType.BUY, "1", 5))));
    }
}
