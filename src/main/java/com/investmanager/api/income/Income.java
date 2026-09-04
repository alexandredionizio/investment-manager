package com.investmanager.api.income;

import com.investmanager.api.asset.Asset;
import com.investmanager.api.portfolio.Portfolio;
import jakarta.persistence.*;
import org.apache.logging.log4j.util.Lazy;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "incomes")
public class Income {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "portfolio_id", nullable = false)
    private Portfolio portfolio;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "asset_id", nullable = false)
    private Asset asset;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private IncomeType type;

    @Column(name = "amount_per_unit", nullable = false, precision = 19, scale = 8)
    private BigDecimal amountPerUnit;

    @Column(nullable = false, precision = 19, scale = 8)
    private BigDecimal quantity;

    @Column(name = "payment_date", nullable = false)
    private LocalDate paymentDate;

    public Income() {
    }



    public Income(
            Portfolio portfolio,
            Asset asset,
            IncomeType type,
            BigDecimal amountPerUnit,
            BigDecimal quantity,
            LocalDate paymentDate) {

        this.portfolio = portfolio;
        this.asset = asset;
        this.type = type;
        this.amountPerUnit = amountPerUnit;
        this.quantity = quantity;
        this.paymentDate = paymentDate;
    }

    public Long getId() {
        return id;
    }

    public Portfolio getPortfolio() {
        return portfolio;
    }

    public Asset getAsset() {
        return asset;
    }

    public IncomeType getType() {
        return type;
    }

    public BigDecimal getAmountPerUnit() {
        return amountPerUnit;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public LocalDate getPaymentDate() {
        return paymentDate;
    }
}
