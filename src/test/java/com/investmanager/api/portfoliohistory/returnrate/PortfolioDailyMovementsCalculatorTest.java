package com.investmanager.api.portfoliohistory.returnrate;

import com.investmanager.api.asset.Asset;
import com.investmanager.api.income.Income;
import com.investmanager.api.income.IncomeType;
import com.investmanager.api.portfolio.Portfolio;
import com.investmanager.api.transaction.Transaction;
import com.investmanager.api.transaction.TransactionType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PortfolioDailyMovementsCalculatorTest {

    private final PortfolioDailyMovementsCalculator calculator =
            new PortfolioDailyMovementsCalculator();

    @Test
    void shouldConsolidatePurchasesSalesAndIncomesByDate() {

        Portfolio portfolio = new Portfolio();
        Asset asset = new Asset();

        LocalDate firstDate = LocalDate.of(2026, 9, 20);
        LocalDate secondDate = LocalDate.of(2026, 9, 21);

        Transaction firstPurchase = new Transaction(
                portfolio,
                asset,
                TransactionType.BUY,
                new BigDecimal("10"),
                new BigDecimal("100.00"),
                firstDate
        );

        Transaction secondPurchase = new Transaction(
                portfolio,
                asset,
                TransactionType.BUY,
                new BigDecimal("5"),
                new BigDecimal("100.00"),
                firstDate
        );

        Transaction sale = new Transaction(
                portfolio,
                asset,
                TransactionType.SELL,
                new BigDecimal("2"),
                new BigDecimal("120.00"),
                secondDate
        );

        Income income = new Income(
                portfolio,
                asset,
                IncomeType.DIVIDEND,
                new BigDecimal("2.00"),
                new BigDecimal("15"),
                firstDate,
                secondDate
        );

        List<PortfolioDailyMovements> result = calculator.calculate(
                List.of(firstPurchase, secondPurchase, sale),
                List.of(income)
        );

        assertEquals(2, result.size());

        PortfolioDailyMovements firstDay = result.get(0);

        assertEquals(firstDate, firstDay.date());
        assertEquals(
                0,
                firstDay.purchases().compareTo(new BigDecimal("1500.00"))
        );
        assertEquals(
                0,
                firstDay.sales().compareTo(BigDecimal.ZERO)
        );
        assertEquals(
                0,
                firstDay.incomes().compareTo(BigDecimal.ZERO)
        );

        PortfolioDailyMovements secondDay = result.get(1);

        assertEquals(secondDate, secondDay.date());
        assertEquals(
                0,
                secondDay.purchases().compareTo(BigDecimal.ZERO)
        );
        assertEquals(
                0,
                secondDay.sales().compareTo(new BigDecimal("240.00"))
        );
        assertEquals(
                0,
                secondDay.incomes().compareTo(new BigDecimal("30.00"))
        );
    }

    @Test
    void shouldUsePaymentDateForIncome() {

        Portfolio portfolio = new Portfolio();
        Asset asset = new Asset();

        LocalDate baseDate = LocalDate.of(2026, 9, 10);
        LocalDate paymentDate = LocalDate.of(2026, 9, 20);

        Income income = new Income(
                portfolio,
                asset,
                IncomeType.DIVIDEND,
                new BigDecimal("1.50"),
                new BigDecimal("100"),
                baseDate,
                paymentDate
        );

        List<PortfolioDailyMovements> result = calculator.calculate(
                List.of(),
                List.of(income)
        );

        assertEquals(1, result.size());
        assertEquals(paymentDate, result.get(0).date());
        assertEquals(
                0,
                result.get(0).incomes()
                        .compareTo(new BigDecimal("150.00"))
        );
    }
}