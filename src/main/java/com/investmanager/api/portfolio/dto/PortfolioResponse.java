package com.investmanager.api.portfolio.dto;

import java.time.LocalDateTime;

public record PortfolioResponse(

        Long id,
        String name,
        String description,
        LocalDateTime createdAt
) {
}
