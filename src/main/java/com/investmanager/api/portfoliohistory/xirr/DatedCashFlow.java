package com.investmanager.api.portfoliohistory.xirr;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

/** Signed investor-perspective cash flow: contribution negative, withdrawal positive. */
public record DatedCashFlow(LocalDate date, BigDecimal amount) {
    public DatedCashFlow {
        Objects.requireNonNull(date, "date");
        Objects.requireNonNull(amount, "amount");
    }
}
