package com.investmanager.api.transaction.concurrency;

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "portfolio_asset_locks")
public class PortfolioAssetLock {

    @EmbeddedId
    private PortfolioAssetLockId id;

    protected PortfolioAssetLock() {
    }

    public PortfolioAssetLockId getId() {
        return id;
    }
}
