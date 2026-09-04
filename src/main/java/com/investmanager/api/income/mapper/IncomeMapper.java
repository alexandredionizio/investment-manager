package com.investmanager.api.income.mapper;

import com.investmanager.api.income.Income;
import com.investmanager.api.income.dto.IncomeResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface IncomeMapper {


    @Mapping(source = "portfolio.id", target = "portfolioId")
    @Mapping(source = "asset.id", target = "assetId")
    @Mapping(source = "asset.ticker", target = "assetTicker")
    @Mapping(
            target = "totalAmount",
            expression = "java(income.getAmountPerUnit().multiply(income.getQuantity()))"
            )
    /* Exemplo do Mapping acima:
    amountPerUnit = 0.35
        quantity  = 100
        0.35 × 100 = 35.00 => totalAmount
     */
    IncomeResponse toResponse(Income income);
}
