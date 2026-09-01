package com.investmanager.api.transaction.mapper;

import com.investmanager.api.transaction.dto.TransactionResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import com.investmanager.api.transaction.Transaction;

@Mapper(componentModel = "spring")
public interface TransactionMapper {

    @Mapping(source = "portfolio.id",target = "portfolioId")
    @Mapping(source = "asset.id",target = "assetId")
    @Mapping(source = "asset.ticker", target = "assetTicker")
    TransactionResponse toResponse(Transaction transaction);
}
