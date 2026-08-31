package com.investmanager.api.portfolio.repository;

import com.investmanager.api.asset.Asset;
import com.investmanager.api.asset.AssetType;
import com.investmanager.api.asset.repository.AssetRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
@Testcontainers
class AssetRepositoryIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres =
            new PostgreSQLContainer("postgres:17-alpine");

    @Autowired
    private AssetRepository assetRepository;

    @Test
    void shouldSaveAndFindAsset() {
        Asset asset = new Asset();
        asset.setTicker("BBAS3");
        asset.setName("Banco do Brasil");
        asset.setType(AssetType.STOCK);
        asset.setSector("Financeiro");
        asset.setExchange("B3");

        Asset savedAsset = assetRepository.save(asset);

        Optional<Asset> foundAsset = assetRepository.findById(savedAsset.getId());

        assertTrue(foundAsset.isPresent());
        assertEquals("BBAS3",foundAsset.get().getTicker());
        assertEquals("Banco do Brasil",foundAsset.get().getName());
        assertEquals(AssetType.STOCK, foundAsset.get().getType());
        assertEquals("Financeiro", foundAsset.get().getSector());
        assertEquals("B3", foundAsset.get().getExchange());
    }

}
