package com.investmanager.api.portfoliohistory.xirr;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

/** An actual transfer across the portfolio boundary, NOT a buy, sell or dividend. */
public record ExternalCapitalMovement(LocalDate date, Kind kind, BigDecimal amount) {
    public enum Kind { CONTRIBUTION, WITHDRAWAL }

    public ExternalCapitalMovement {
        Objects.requireNonNull(date, "date");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(amount, "amount");
        if (amount.signum() <= 0)
            throw new IllegalArgumentException("External capital amount must be positive");
    }
}
