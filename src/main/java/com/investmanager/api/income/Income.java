package com.investmanager.api.income;

import com.investmanager.api.asset.Asset;
import com.investmanager.api.portfolio.Portfolio;
import jakarta.persistence.*;

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

    @Column(name = "base_date")
    private LocalDate baseDate;

    @Column(name = "payment_date", nullable = false)
    private LocalDate paymentDate;

    /** Crédito líquido exato e chave idempotente somente para importações B3. */
    @Column(name = "imported_credit_amount", precision = 19, scale = 8)
    private BigDecimal importedCreditAmount;

    @Column(name = "b3_income_fingerprint", length = 64)
    private String b3IncomeFingerprint;

    public BigDecimal getTotalAmount() {
        return importedCreditAmount != null ? importedCreditAmount : amountPerUnit.multiply(quantity);
    }

    public void markImportedFromB3(BigDecimal credited, String fingerprint) {
        if (credited == null || credited.signum() <= 0 || fingerprint == null || fingerprint.length() != 64)
            throw new IllegalArgumentException("Crédito B3 inválido");
        this.importedCreditAmount = credited;
        this.b3IncomeFingerprint = fingerprint;
    }

    /** Recebimento já confirmado pela B3: sem inventar data-base ou posição. */
    public static Income fromB3Receipt(Portfolio portfolio, Asset asset, IncomeType type,
                                      BigDecimal amountPerUnit, BigDecimal quantity,
                                      LocalDate paymentDate, BigDecimal credited, String fingerprint) {
        Income income = new Income(portfolio, asset, type, amountPerUnit, quantity, null, paymentDate);
        income.markImportedFromB3(credited, fingerprint);
        return income;
    }

    public Income() {
    }

    public Income(
            Portfolio portfolio,
            Asset asset,
            IncomeType type,
            BigDecimal amountPerUnit,
            BigDecimal quantity,
            LocalDate baseDate,
            LocalDate paymentDate) {

        this.portfolio = portfolio;
        this.asset = asset;
        this.type = type;
        this.amountPerUnit = amountPerUnit;
        this.quantity = quantity;
        this.baseDate = baseDate;
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

    public LocalDate getBaseDate() {
        return baseDate;
    }

    public LocalDate getPaymentDate() {
        return paymentDate;
    }
}