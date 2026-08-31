package com.investmanager.api.asset.service;

import com.investmanager.api.asset.Asset;
import com.investmanager.api.asset.repository.AssetRepository;
import com.investmanager.api.asset.AssetType;
import com.investmanager.api.asset.dto.AssetResponse;
import com.investmanager.api.asset.dto.CreateAssetRequest;
import com.investmanager.api.shared.exception.AssetNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class AssetServiceTest {

    @Mock
    private AssetRepository assetRepository;

    private AssetService assetService;

    @BeforeEach
    void setUp() {
        assetService = new AssetService(assetRepository);
    }

    @Test
    void shouldReturnAssetWhenIdExists() {
        Asset asset = new Asset();
        asset.setTicker("ITUB4");
        asset.setName("Itau Unibanco");
        asset.setType(AssetType.STOCK);
        asset.setSector("Financeiro");
        asset.setExchange("B3");

        when(assetRepository.findById(1L))
                .thenReturn(Optional.of(asset));

        AssetResponse response = assetService.findById(1L);

        assertEquals("ITUB4", response.ticker());

    }

    @Test
    void shouldThrowExceptionWhenIdDoesNotExist(){
        when(assetRepository.findById(999L))
                .thenReturn(Optional.empty());

        assertThrows(
                AssetNotFoundException.class,
                () -> assetService.findById(999L)
        );
    }

    @Test
    void shouldReturnAllAssets() {

        Asset asset1 = new Asset();
        asset1.setTicker("ITUB4");
        asset1.setName("Itau Unibanco");
        asset1.setType(AssetType.STOCK);
        asset1.setSector("Financeiro");
        asset1.setExchange("B3");

        Asset asset2 = new Asset();
        asset2.setTicker("PETR4");
        asset2.setName("Petrobras");
        asset2.setType(AssetType.STOCK);
        asset2.setSector("Petroleo e Gas");
        asset2.setExchange("B3");

        when(assetRepository.findAll())
                .thenReturn(List.of(asset1, asset2));

        List<AssetResponse> response = assetService.findAll();

        assertEquals(2, response.size());
        assertEquals("ITUB4", response.get(0).ticker());
        assertEquals("PETR4", response.get(1).ticker());
    }

    @Test
    void shouldCreateAsset() {

        CreateAssetRequest request = new CreateAssetRequest(
                "VALE3",
                "Vale",
                AssetType.STOCK,
                "Mineracao",
                "B3"
        );

        Asset savedAsset = new Asset();
        savedAsset.setTicker("VALE3");
        savedAsset.setName("Vale");
        savedAsset.setType(AssetType.STOCK);
        savedAsset.setSector("Mineracao");
        savedAsset.setExchange("B3");

        when(assetRepository.save(any(Asset.class)))
                .thenReturn(savedAsset);

        AssetResponse response = assetService.create(request);

        ArgumentCaptor<Asset> assetCaptor = ArgumentCaptor.forClass(Asset.class);

        verify(assetRepository).save(assetCaptor.capture());

        Asset capturedAsset = assetCaptor.getValue();

        assertEquals("VALE3", response.ticker());
        assertEquals("Vale", response.name());
        assertEquals(AssetType.STOCK, response.type());

        assertEquals("VALE3", capturedAsset.getTicker());
        assertEquals("Vale", capturedAsset.getName());
        assertEquals(AssetType.STOCK, capturedAsset.getType());
        assertEquals("Mineracao", capturedAsset.getSector());
        assertEquals("B3", capturedAsset.getExchange());
    }

}
