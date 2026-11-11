package com.investmanager.api.transaction.service;

import com.investmanager.api.income.Income;
import com.investmanager.api.income.repository.IncomeRepository;
import com.investmanager.api.transaction.Transaction;
import com.investmanager.api.transaction.TransactionType;
import com.investmanager.api.transaction.concurrency.PortfolioAssetLockManager;
import com.investmanager.api.transaction.concurrency.TransactionCreationExecutor;
import com.investmanager.api.transaction.repository.TransactionRepository;
import jakarta.persistence.EntityManager;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;

/** IM-052: only manual transactions can be deleted; B3 import records remain immutable. */
@Service
public class TransactionDeletionService {
    private final TransactionRepository transactions;
    private final IncomeRepository incomes;
    private final PortfolioAssetLockManager locks;
    private final TransactionCreationExecutor executor;
    private final EntityManager entityManager;

    public TransactionDeletionService(TransactionRepository transactions, IncomeRepository incomes,
                                      PortfolioAssetLockManager locks, TransactionCreationExecutor executor,
                                      EntityManager entityManager) {
        this.transactions = transactions;
        this.incomes = incomes;
        this.locks = locks;
        this.executor = executor;
        this.entityManager = entityManager;
    }

    public void deleteManual(Long transactionId, Long userId) {
        executor.execute(() -> {
            // Authorization is checked against the transaction owner before obtaining a lock.
            Transaction original = transactions.findByIdAndPortfolioUserId(transactionId, userId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Transacao nao encontrada."));
            Long portfolioId = original.getPortfolio().getId();
            Long assetId = original.getAsset().getId();
            locks.acquire(portfolioId, assetId);

            // Re-read under the same lock to protect against concurrent modification/deletion.
            Transaction target = transactions.findByIdAndPortfolioUserId(transactionId, userId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Transacao nao encontrada."));

            Number importedCount = (Number) entityManager.createNativeQuery(
                            "select count(*) from b3_import_batch_rows where transaction_id = :id")
                    .setParameter("id", transactionId).getSingleResult();
            if (importedCount.longValue() > 0) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Operacao importada da B3 nao pode ser excluida. E necessario um fluxo de correcao auditavel.");
            }

            List<Transaction> history = transactions.findByPortfolioIdAndAssetIdOrderByTransactionDateAscIdAsc(
                    portfolioId, assetId);
            List<Income> assetIncomes = incomes.findByPortfolioIdAndAssetIdOrderByBaseDateAscIdAsc(
                    portfolioId, assetId);

            validateRemainingHistory(history, assetIncomes, target.getId());
            transactions.delete(target);
            transactions.flush();
            return null;
        });
    }

    /** Replays quantities chronologically, checking every intermediate sale and proventos' base dates. */
    static void validateRemainingHistory(List<Transaction> orderedHistory, List<Income> orderedIncomes, Long deletedId) {
        List<Transaction> remaining = orderedHistory.stream().filter(t -> !t.getId().equals(deletedId)).toList();
        BigDecimal quantity = BigDecimal.ZERO;
        int operation = 0;
        for (Income income : orderedIncomes) {
            if (income.getBaseDate() == null) continue; // Crédito B3 recebido não tem data-com.
            while (operation < remaining.size() &&
                    !remaining.get(operation).getTransactionDate().isAfter(income.getBaseDate())) {
                quantity = apply(quantity, remaining.get(operation++));
            }
            if (quantity.compareTo(income.getQuantity()) != 0) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Exclusao bloqueada: o saldo na data-base de um provento seria alterado.");
            }
        }
        while (operation < remaining.size()) {
            quantity = apply(quantity, remaining.get(operation++));
        }
    }

    private static BigDecimal apply(BigDecimal balance, Transaction transaction) {
        BigDecimal result = transaction.getType() == TransactionType.BUY
                ? balance.add(transaction.getQuantity()) : balance.subtract(transaction.getQuantity());
        if (result.signum() < 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Exclusao bloqueada: deixaria uma venda posterior sem saldo suficiente.");
        }
        return result;
    }
}
