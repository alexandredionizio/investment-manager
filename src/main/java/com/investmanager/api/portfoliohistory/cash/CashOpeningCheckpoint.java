package com.investmanager.api.portfoliohistory.cash;

import com.investmanager.api.portfolio.Portfolio;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/** One user-attested end-of-day opening cash balance, without retroactive calculation. */
@Entity
@Table(name = "portfolio_cash_opening_checkpoints")
public class CashOpeningCheckpoint {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "portfolio_id", nullable = false)
    private Portfolio portfolio;
    @Column(name = "checkpoint_date", nullable = false)
    private LocalDate checkpointDate;
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal balance;
    @Column(name = "source_description", nullable = false, length = 255)
    private String sourceDescription;
    @Column(name = "confirmed_at", nullable = false, updatable = false)
    private OffsetDateTime confirmedAt;

    protected CashOpeningCheckpoint() {}
    public CashOpeningCheckpoint(Portfolio portfolio, LocalDate checkpointDate, BigDecimal balance, String sourceDescription) {
        this.portfolio = portfolio;
        this.checkpointDate = checkpointDate;
        this.balance = balance;
        this.sourceDescription = sourceDescription;
        this.confirmedAt = OffsetDateTime.now();
    }
    public LocalDate getCheckpointDate() { return checkpointDate; }
    public BigDecimal getBalance() { return balance; }
    public String getSourceDescription() { return sourceDescription; }
    public OffsetDateTime getConfirmedAt() { return confirmedAt; }
}
