package com.investmanager.api.income.dto;

import com.investmanager.api.income.IncomeType;

import java.math.BigDecimal;
import java.time.LocalDate;

public record IncomeResponse(

        Long id,
        Long portfolioId,
        Long assetId,
        String assetTicker,
        IncomeType type,
        BigDecimal amountPerUnit,
        BigDecimal quantity,
        BigDecimal totalAmount,
        LocalDate paymentDate
) {
}
