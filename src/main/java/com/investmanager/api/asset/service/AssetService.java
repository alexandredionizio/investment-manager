package com.investmanager.api.asset.service;

import com.investmanager.api.asset.Asset;
import com.investmanager.api.asset.AssetRepository;
import com.investmanager.api.asset.dto.AssetResponse;
import com.investmanager.api.asset.dto.CreateAssetRequest;
import com.investmanager.api.shared.exception.AssetNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;

@Service
public class AssetService {
    private final AssetRepository assetRepository;

    public AssetService(AssetRepository assetRepository) {
        this.assetRepository = assetRepository;
    }

    public AssetResponse create(CreateAssetRequest request) {
        Asset asset = new Asset();

        asset.setTicker(request.ticker());
        asset.setName(request.name());
        asset.setType(request.type());
        asset.setSector(request.sector());
        asset.setExchange(request.exchange());

        Asset savedAsset = assetRepository.save(asset);

        return new AssetResponse(
                savedAsset.getId(),
                savedAsset.getTicker(),
                savedAsset.getName(),
                savedAsset.getType(),
                savedAsset.getSector(),
                savedAsset.getExchange()
        );

    }

    public AssetResponse findById(Long id) {

        Asset asset = assetRepository.findById(id)
                .orElseThrow(() -> new AssetNotFoundException(id));

        return new AssetResponse(
                asset.getId(),
                asset.getTicker(),
                asset.getName(),
                asset.getType(),
                asset.getSector(),
                asset.getExchange()
        );
    }

    public List<AssetResponse> findAll() {

        return assetRepository.findAll()
                .stream()
                .map(asset -> new AssetResponse(
                        asset.getId(),
                        asset.getTicker(),
                        asset.getName(),
                        asset.getType(),
                        asset.getSector(),
                        asset.getExchange()
                ))
                .toList();
    }

}
