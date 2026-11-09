package com.investmanager.api.income.service;

import com.investmanager.api.income.Income;
import com.investmanager.api.income.exception.IncomeEligibilityConflictException;
import com.investmanager.api.income.repository.IncomeRepository;
import com.investmanager.api.transaction.Transaction;
import com.investmanager.api.transaction.TransactionType;
import com.investmanager.api.transaction.repository.TransactionRepository;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import java.util.List;

@Component
public class IncomeEligibilityValidator {
    private final IncomeRepository incomes;
    private final TransactionRepository transactions;

    public IncomeEligibilityValidator(IncomeRepository incomes, TransactionRepository transactions) {
        this.incomes = incomes;
        this.transactions = transactions;
    }

    // Called inside the transaction holding the portfolio/asset lock.
    public void validate(Long portfolioId, Long assetId, Transaction candidate) {
        List<Income> affected = incomes
                .findByPortfolioIdAndAssetIdAndBaseDateGreaterThanEqualOrderByBaseDateAscIdAsc(
                        portfolioId, assetId, candidate.getTransactionDate());
        if (affected.isEmpty()) return;

        List<Transaction> history = transactions.findByPortfolioIdAndAssetIdOrderByTransactionDateAscIdAsc(
                portfolioId, assetId);
        BigDecimal quantity = BigDecimal.ZERO;
        BigDecimal delta = signedQuantity(candidate);
        int index = 0;
        for (Income income : affected) {
            while (index < history.size() && !history.get(index).getTransactionDate().isAfter(income.getBaseDate())) {
                quantity = quantity.add(signedQuantity(history.get(index++)));
            }
            // Eligibility includes every operation on baseDate; scale differences are not conflicts.
            if (quantity.add(delta).compareTo(income.getQuantity()) != 0) {
                throw new IncomeEligibilityConflictException(income.getId(), income.getBaseDate());
            }
        }
    }

    private BigDecimal signedQuantity(Transaction transaction) {
        return transaction.getType() == TransactionType.BUY
                ? transaction.getQuantity() : transaction.getQuantity().negate();
    }
}
