package com.investmanager.api.asset.repository;

import com.investmanager.api.asset.Asset;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AssetRepository extends JpaRepository<Asset, Long> {
}
