package com.investmanager.api.asset.mapper;

import com.investmanager.api.asset.Asset;
import com.investmanager.api.asset.dto.AssetResponse;
import com.investmanager.api.asset.dto.CreateAssetRequest;
import org.mapstruct.Mapper;
import org.springframework.stereotype.Component;

@Mapper(componentModel = "spring")
public interface AssetMapper {

    Asset toEntity(CreateAssetRequest request);
    AssetResponse toResponse(Asset asset);
}
