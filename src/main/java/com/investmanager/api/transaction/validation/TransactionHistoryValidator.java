package com.investmanager.api.transaction.validation;

import com.investmanager.api.position.exception.InsufficientPositionException;
import com.investmanager.api.transaction.Transaction;
import com.investmanager.api.transaction.TransactionType;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

@Component
public class TransactionHistoryValidator {

    /**
     * Recebe o histórico da mesma carteira e ativo, ordenado por transactionDate e ID.
     * A venda candidata é considerada após todas as operações existentes na mesma data.
     */
    public void validateSale(List<Transaction> history, Transaction candidate) {
        BigDecimal quantity = BigDecimal.ZERO;
        boolean candidateApplied = false;

        for (Transaction transaction : history) {
            if (!candidateApplied
                    && transaction.getTransactionDate().isAfter(candidate.getTransactionDate())) {
                quantity = apply(quantity, candidate);
                candidateApplied = true;
            }

            quantity = apply(quantity, transaction);
        }

        if (!candidateApplied) {
            apply(quantity, candidate);
        }
    }

    private BigDecimal apply(BigDecimal quantity, Transaction transaction) {
        BigDecimal updatedQuantity = transaction.getType() == TransactionType.BUY
                ? quantity.add(transaction.getQuantity())
                : quantity.subtract(transaction.getQuantity());

        if (updatedQuantity.compareTo(BigDecimal.ZERO) < 0) {
            throw new InsufficientPositionException(transaction.getAsset().getTicker());
        }

        return updatedQuantity;
    }
}
