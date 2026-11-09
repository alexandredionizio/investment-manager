package com.investmanager.api.asset.repository;

import com.investmanager.api.asset.Asset;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AssetRepository extends JpaRepository<Asset, Long> {
    List<Asset> findByTickerIgnoreCase(String ticker);
    @org.springframework.data.jpa.repository.Query("select a from Asset a where upper(trim(a.ticker)) = :ticker")
    List<Asset> findByNormalizedTicker(@org.springframework.data.repository.query.Param("ticker") String ticker);
}
