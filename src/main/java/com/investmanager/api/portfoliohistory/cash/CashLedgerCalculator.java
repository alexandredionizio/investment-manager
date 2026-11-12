package com.investmanager.api.portfoliohistory.cash;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Read-only cash reconciliation. Never assumes a zero opening balance for legacy portfolios. */
public final class CashLedgerCalculator {
    public enum Kind { CONTRIBUTION, WITHDRAWAL, BUY, SELL, RECEIVED_INCOME }
    public enum Status { COMPLETE, OPENING_BALANCE_UNKNOWN, NEGATIVE_CASH }

    public record Entry(LocalDate date, Kind kind, BigDecimal amount) {
        public Entry {
            Objects.requireNonNull(date, "date");
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(amount, "amount");
            if (amount.signum() <= 0) throw new IllegalArgumentException("Cash amount must be positive");
        }
    }
    public record Result(Status status, Optional<BigDecimal> balance, LocalDate firstDeficitDate) {
        public Result {
            Objects.requireNonNull(status, "status");
            Objects.requireNonNull(balance, "balance");
        }
    }

    /**
     * Balances are reconciled at the end of each calendar day (no intraday timestamps exist).
     * An unknown opening balance is not silently replaced by zero. A historical deficit
     * yields no usable closing balance, even if later contributions restore a positive sum.
     * Caller must provide a VERIFIED opening balance and ALL relevant events through asOf.
     */
    public Result calculate(Optional<BigDecimal> verifiedOpeningBalance, LocalDate openingDate,
                            LocalDate asOf, List<Entry> entries) {
        Objects.requireNonNull(verifiedOpeningBalance, "verifiedOpeningBalance");
        Objects.requireNonNull(openingDate, "openingDate");
        Objects.requireNonNull(asOf, "asOf");
        Objects.requireNonNull(entries, "entries");
        if (asOf.isBefore(openingDate)) throw new IllegalArgumentException("asOf before openingDate");
        if (verifiedOpeningBalance.isEmpty())
            return new Result(Status.OPENING_BALANCE_UNKNOWN, Optional.empty(), null);
        BigDecimal balance = verifiedOpeningBalance.get();
        if (balance.signum() < 0) throw new IllegalArgumentException("Negative opening balance");
        List<Entry> ordered = new ArrayList<>(entries);
        for (Entry entry : ordered) {
            Objects.requireNonNull(entry, "entry");
            if (entry.date().isBefore(openingDate))
                throw new IllegalArgumentException("Event precedes verified opening balance");
        }
        ordered.sort(Comparator.comparing(Entry::date));
        int i = 0;
        while (i < ordered.size() && !ordered.get(i).date().isAfter(asOf)) {
            LocalDate day = ordered.get(i).date();
            BigDecimal netForDay = BigDecimal.ZERO;
            while (i < ordered.size() && ordered.get(i).date().equals(day)) {
                Entry e = ordered.get(i++);
                BigDecimal delta = switch(e.kind()) {
                    case CONTRIBUTION, SELL, RECEIVED_INCOME -> e.amount();
                    case WITHDRAWAL, BUY -> e.amount().negate();
                };
                netForDay = netForDay.add(delta);
            }
            balance = balance.add(netForDay);
            if (balance.signum() < 0)
                return new Result(Status.NEGATIVE_CASH, Optional.empty(), day);
        }
        return new Result(Status.COMPLETE, Optional.of(balance), null);
    }
}
