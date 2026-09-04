package com.investmanager.api.income.dto;

import com.investmanager.api.income.IncomeType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDate;

public record IncomeRequest(

        @NotNull(message = "A carteira é obrigatória")
        Long portfolioId,

        @NotNull(message = "O ativo é obrigatório")
        Long assetId,

        @NotNull(message = "O tipo do provento é obrigatório")
        IncomeType type,

        @NotNull(message = "O valor por unidade é obrigatório")
        @Positive(message = "O valor por unidade deve ser maior que zero")
        BigDecimal amountPerUnit,

        @NotNull(message = "A quantidade é obrigatória")
        @Positive(message = "A quantidade deve ser maior que zero")
        BigDecimal quantity,

        @NotNull(message = "A data de pagamento é obrigatória")
        LocalDate paymentDate

) {
}
