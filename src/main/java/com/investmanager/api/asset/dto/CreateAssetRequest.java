package com.investmanager.api.asset.dto;

import com.investmanager.api.asset.AssetType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateAssetRequest(

        @NotBlank(message = "O ticker é obrigatório")
        @Size(max = 20, message = "O ticker deve possuir no máximo 20 caracteres")
        String ticker,

        @NotBlank(message = "O nome do ativo é obrigatório")
        @Size(max = 120, message = "O nome do ativo deve possuir no máximo 120 caracteres")
        String name,

        @NotNull(message = "O tipo do ativo é obrigatório")
        AssetType type,

        @Size(max = 100, message = "O setor deve possuir no máximo 100 caracteres")
        String sector,

        @Size(max = 50, message = "A bolsa deve possuir no máximo 50 caracteres")
        String exchange
) {
}