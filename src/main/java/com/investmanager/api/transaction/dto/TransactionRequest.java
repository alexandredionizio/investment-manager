package com.investmanager.api.transaction.dto;

import com.investmanager.api.transaction.TransactionType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDate;

public record TransactionRequest(

        @NotNull(message = "A carteira é obrigatória")
        Long portfolioId,

        @NotNull(message = "O ativo é obrigatório")
        Long assetId,

        @NotNull(message = "O tipo da transação é obrigatório")
        TransactionType type,

        @NotNull(message = "A quantidade é obrigatória")
        @Positive(message = "A quantidade deve ser maior que zero")
        BigDecimal quantity,

        @NotNull(message = "O preço unitário é obrigatório")
        @Positive(message = "O preço unitário deve ser maior que zero")
        BigDecimal unitPrice,

        @NotNull(message = "A data da transação é obrigatória")
        LocalDate transactionDate

) {
}
