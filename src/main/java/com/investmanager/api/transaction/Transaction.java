package com.investmanager.api.transaction;

import com.investmanager.api.asset.Asset;
import com.investmanager.api.broker.Broker;
import com.investmanager.api.portfolio.Portfolio;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "transactions")
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "portfolio_id", nullable = false)
    private Portfolio portfolio;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "asset_id", nullable = false)
    private Asset asset;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "broker_id")
    private Broker broker;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TransactionType type;

    @Column(nullable = false, precision = 19, scale = 8)
    private BigDecimal quantity;

    @Column(name = "unit_price", nullable = false, precision = 19, scale = 8)
    private BigDecimal unitPrice;

    @Column(name = "transaction_date", nullable = false)
    private LocalDate transactionDate;

    public Transaction() {
    }

    public Broker getBroker() {
        return broker;
    }

    public void setBroker(Broker broker) {
        this.broker = broker;
    }

    public void setPortfolio(Portfolio portfolio) {
        this.portfolio = portfolio;
    }

    public void setAsset(Asset asset) {
        this.asset = asset;
    }

    public void setType(TransactionType type) {
        this.type = type;
    }

    public void setQuantity(BigDecimal quantity) {
        this.quantity = quantity;
    }

    public void setUnitPrice(BigDecimal unitPrice) {
        this.unitPrice = unitPrice;
    }

    public void setTransactionDate(LocalDate transactionDate) {
        this.transactionDate = transactionDate;
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

    public TransactionType getType() {
        return type;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public LocalDate getTransactionDate() {
        return transactionDate;
    }

    public Transaction(
            Portfolio portfolio,
            Asset asset,
            TransactionType type,
            BigDecimal quantity,
            BigDecimal unitPrice,
            LocalDate transactionDate) {

        this.portfolio = portfolio;
        this.asset = asset;
        this.type = type;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
        this.transactionDate = transactionDate;
    }

    public Transaction(
            Portfolio portfolio,
            Asset asset,
            Broker broker,
            TransactionType type,
            BigDecimal quantity,
            BigDecimal unitPrice,
            LocalDate transactionDate) {

        this.portfolio = portfolio;
        this.asset = asset;
        this.broker = broker;
        this.type = type;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
        this.transactionDate = transactionDate;
    }
}
