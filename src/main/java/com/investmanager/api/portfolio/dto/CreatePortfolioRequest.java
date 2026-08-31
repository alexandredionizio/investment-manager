package com.investmanager.api.portfolio.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreatePortfolioRequest(

        @NotBlank
        @Size(max = 100)
        String name,

        @Size(max = 255)
        String description

) {
}
