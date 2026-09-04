package com.investmanager.api.transaction.dto;

import com.investmanager.api.transaction.TransactionType;

import java.math.BigDecimal;
import java.time.LocalDate;

public record TransactionResponse(

        Long id,
        Long portfolioId,
        Long assetId,
        String assetTicker,
        Long brokerId,
        String brokerName,
        TransactionType type,
        BigDecimal quantity,
        BigDecimal unitPrice,
        LocalDate transactionDate
) {
}
