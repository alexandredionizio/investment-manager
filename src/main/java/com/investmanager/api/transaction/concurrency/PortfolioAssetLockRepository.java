package com.investmanager.api.transaction.concurrency;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface PortfolioAssetLockRepository extends JpaRepository<PortfolioAssetLock, PortfolioAssetLockId> {

    @Modifying
    @Query(value = """
            INSERT INTO portfolio_asset_locks (portfolio_id, asset_id)
            VALUES (:portfolioId, :assetId)
            ON CONFLICT (portfolio_id, asset_id) DO NOTHING
            """, nativeQuery = true)
    void ensureExists(@Param("portfolioId") Long portfolioId, @Param("assetId") Long assetId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select coordination from PortfolioAssetLock coordination where coordination.id = :id")
    Optional<PortfolioAssetLock> findForUpdate(@Param("id") PortfolioAssetLockId id);
}
