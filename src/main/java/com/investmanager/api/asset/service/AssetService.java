package com.investmanager.api.asset.service;

import com.investmanager.api.asset.Asset;
import com.investmanager.api.asset.mapper.AssetMapper;
import com.investmanager.api.asset.repository.AssetRepository;
import com.investmanager.api.asset.dto.AssetResponse;
import com.investmanager.api.asset.dto.CreateAssetRequest;
import com.investmanager.api.asset.exception.AssetNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AssetService {
    private final AssetRepository assetRepository;

    private final AssetMapper assetMapper;

    public AssetService(
            AssetRepository assetRepository,
            AssetMapper assetMapper) {

        this.assetRepository = assetRepository;
        this.assetMapper = assetMapper;
    }

    public AssetResponse create(CreateAssetRequest request) {
        Asset asset = assetMapper.toEntity(request);

        Asset savedAsset = assetRepository.save(asset);

        return assetMapper.toResponse(savedAsset);

    }


    public AssetResponse findById(Long id) {

        Asset asset = assetRepository.findById(id)
                .orElseThrow(() -> new AssetNotFoundException(id));

        return assetMapper.toResponse(asset);
    }

    public List<AssetResponse> findAll() {

        return assetRepository.findAll()
                .stream()
                .map(assetMapper::toResponse)
                .toList();
    }

}
