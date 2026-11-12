package com.investmanager.api.portfoliohistory.capital;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record CapitalMovementRequest(
        @NotNull UUID requestId,
        @NotNull CapitalMovement.Kind kind,
        @NotNull LocalDate date,
        @NotNull @DecimalMin("0.01") @Digits(integer = 17, fraction = 2) BigDecimal amount,
        @Size(max = 255) String description) {}
