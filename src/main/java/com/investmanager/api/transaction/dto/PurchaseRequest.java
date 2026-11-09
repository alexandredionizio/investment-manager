package com.investmanager.api.transaction.dto;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;
public record PurchaseRequest(
        @NotNull @Positive Long portfolioId,
        @NotBlank @Pattern(regexp="(?i)[A-Z]{4}[0-9]{1,2}") String ticker,
        @NotNull @Positive Long brokerId,
        @NotNull @Positive @Digits(integer=11, fraction=8) BigDecimal quantity,
        @NotNull @Positive @Digits(integer=11, fraction=8) BigDecimal unitPrice,
        @NotNull LocalDate transactionDate) {}
