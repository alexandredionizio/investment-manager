package com.investmanager.api.portfoliohistory.returnrate;

import com.investmanager.api.income.Income;
import com.investmanager.api.transaction.Transaction;
import com.investmanager.api.transaction.TransactionType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class PortfolioDailyMovementsCalculator {

    public List<PortfolioDailyMovements> calculate(
            List<Transaction> transactions,
            List<Income> incomes) {

        Map<LocalDate, DailyTotals> totalsByDate = new TreeMap<>();

        for (Transaction transaction : transactions) {

            DailyTotals totals = totalsByDate.computeIfAbsent(
                    transaction.getTransactionDate(),
                    date -> new DailyTotals()
            );

            BigDecimal transactionValue = transaction.getQuantity()
                    .multiply(transaction.getUnitPrice());

            if (transaction.getType() == TransactionType.BUY) {
                totals.purchases = totals.purchases.add(transactionValue);
            } else if (transaction.getType() == TransactionType.SELL) {
                totals.sales = totals.sales.add(transactionValue);
            }
        }

        for (Income income : incomes) {

            DailyTotals totals = totalsByDate.computeIfAbsent(
                    income.getPaymentDate(),
                    date -> new DailyTotals()
            );

            BigDecimal incomeValue = income.getAmountPerUnit()
                    .multiply(income.getQuantity());

            totals.incomes = totals.incomes.add(incomeValue);
        }

        return totalsByDate.entrySet()
                .stream()
                .map(entry -> new PortfolioDailyMovements(
                        entry.getKey(),
                        entry.getValue().purchases,
                        entry.getValue().sales,
                        entry.getValue().incomes
                ))
                .toList();
    }

    private static class DailyTotals {

        private BigDecimal purchases = BigDecimal.ZERO;
        private BigDecimal sales = BigDecimal.ZERO;
        private BigDecimal incomes = BigDecimal.ZERO;
    }
}