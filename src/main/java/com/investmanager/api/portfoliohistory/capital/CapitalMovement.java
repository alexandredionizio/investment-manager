package com.investmanager.api.portfoliohistory.capital;

import com.investmanager.api.portfolio.Portfolio;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "portfolio_capital_movements")
public class CapitalMovement {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "portfolio_id", nullable = false)
    private Portfolio portfolio;
    @Column(name = "request_id", nullable = false, updatable = false)
    private UUID requestId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Kind kind;
    @Column(name = "movement_date", nullable = false)
    private LocalDate movementDate;
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;
    @Column(length = 255)
    private String description;
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    public enum Kind { CONTRIBUTION, WITHDRAWAL }
    protected CapitalMovement() {}
    public CapitalMovement(Portfolio portfolio, UUID requestId, Kind kind, LocalDate movementDate,
                           BigDecimal amount, String description) {
        this.portfolio = portfolio;
        this.requestId = requestId;
        this.kind = kind;
        this.movementDate = movementDate;
        this.amount = amount;
        this.description = description;
        this.createdAt = OffsetDateTime.now();
    }
    public Long getId() { return id; }
    public UUID getRequestId() { return requestId; }
    public Kind getKind() { return kind; }
    public LocalDate getMovementDate() { return movementDate; }
    public BigDecimal getAmount() { return amount; }
    public String getDescription() { return description; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
