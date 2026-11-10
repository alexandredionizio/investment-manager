package com.investmanager.api.portfolio.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RenamePortfolioRequest(
        @NotBlank(message = "O nome da carteira é obrigatório")
        @Size(max = 100, message = "O nome da carteira deve ter no máximo 100 caracteres")
        String name
) {}
