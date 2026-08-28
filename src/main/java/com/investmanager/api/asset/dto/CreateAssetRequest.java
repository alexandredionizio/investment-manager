package com.investmanager.api.asset.dto;

import com.investmanager.api.asset.AssetType;
import jakarta.persistence.Column;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateAssetRequest(
        @NotBlank
        @Size(max = 20)
        String ticker,

        @NotBlank
        @Size(max = 120)
        String name,

        @NotNull
        AssetType type,

        @Size(max = 100)
        String sector,

        @Size(max = 50)
        String exchange
) {

}
