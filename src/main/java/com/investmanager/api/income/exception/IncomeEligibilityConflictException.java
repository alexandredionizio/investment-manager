package com.investmanager.api.income.exception;

import java.time.LocalDate;

public class IncomeEligibilityConflictException extends RuntimeException {
    public IncomeEligibilityConflictException(Long incomeId, LocalDate baseDate) {
        super("A transação altera a quantidade elegível do provento " + incomeId
                + " na data-base " + baseDate + ". Reconcilie o provento antes de alterar esse histórico.");
    }
}
