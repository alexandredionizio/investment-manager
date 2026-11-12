package com.investmanager.api.portfoliohistory.cash;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CashReconciliationResponse(
    String status, LocalDate asOf, LocalDate openingDate,
    BigDecimal declaredOpeningBalance, BigDecimal computedCashBalance,
    LocalDate firstDeficitDate, int movementsConsidered, String notice
) {}
