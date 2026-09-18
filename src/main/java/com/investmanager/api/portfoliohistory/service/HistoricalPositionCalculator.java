package com.investmanager.api.portfoliohistory.service;

import com.investmanager.api.transaction.Transaction;
import com.investmanager.api.transaction.TransactionType;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Component
public class HistoricalPositionCalculator {

    public BigDecimal calculateQuantity(
            List<Transaction> transactions,
            LocalDate date) {

        if (transactions == null) {
            throw new IllegalArgumentException(
                    "Lista de transações é obrigatória"
            );
        }

        if (date == null) {
            throw new IllegalArgumentException(
                    "Data é obrigatória"
            );
        }

        BigDecimal quantity =
                BigDecimal.ZERO;

        for (Transaction transaction : transactions) {

            if (transaction.getTransactionDate().isAfter(date)) {
                break;
            }

            if (transaction.getType() == TransactionType.BUY) {
                quantity =
                        quantity.add(
                                transaction.getQuantity()
                        );
            }

            if (transaction.getType() == TransactionType.SELL) {
                quantity =
                        quantity.subtract(
                                transaction.getQuantity()
                        );
            }
        }

        return quantity;
    }
}