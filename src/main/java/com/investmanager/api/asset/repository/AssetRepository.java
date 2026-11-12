package com.investmanager.api.asset.repository;

import com.investmanager.api.asset.Asset;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AssetRepository extends JpaRepository<Asset, Long> {
    List<Asset> findByTickerIgnoreCase(String ticker);
    @org.springframework.data.jpa.repository.Query("select a from Asset a where upper(trim(a.ticker)) = :ticker")
    List<Asset> findByNormalizedTicker(@org.springframework.data.repository.query.Param("ticker") String ticker);
    /** Confirmed B3 ticker identities, including historical aliases. Never guesses by name. */
    @Query(value = """
            select distinct a.* from assets a
            join asset_identifiers i on i.asset_id = a.id
            where i.exchange = 'B3' and i.kind = 'TICKER' and i.value = :ticker
              and a.type in ('STOCK', 'FII', 'ETF')
              and (a.exchange is null or upper(trim(a.exchange)) = 'B3')
            """, nativeQuery = true)
    List<Asset> findByConfirmedB3Ticker(@Param("ticker") String ticker);
}
