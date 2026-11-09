package com.investmanager.api.transaction.concurrency;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.util.Objects;

@Embeddable
public class PortfolioAssetLockId implements Serializable {

    @Column(name = "portfolio_id", nullable = false)
    private Long portfolioId;

    @Column(name = "asset_id", nullable = false)
    private Long assetId;

    protected PortfolioAssetLockId() {
    }

    public PortfolioAssetLockId(Long portfolioId, Long assetId) {
        this.portfolioId = portfolioId;
        this.assetId = assetId;
    }

    public Long getPortfolioId() {
        return portfolioId;
    }

    public Long getAssetId() {
        return assetId;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof PortfolioAssetLockId that)) return false;
        return Objects.equals(portfolioId, that.portfolioId) && Objects.equals(assetId, that.assetId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(portfolioId, assetId);
    }
}
