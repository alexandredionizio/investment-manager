package com.investmanager.api.portfolio.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreatePortfolioRequest(

        @NotBlank(message = "O nome da carteira é obrigatório")
        @Size(max = 100, message = "O nome da carteira deve possuir no máximo 100 caracteres")
        String name,

        @Size(max = 255, message = "A descrição deve possuir no máximo 255 caracteres")
        String description
) {
}