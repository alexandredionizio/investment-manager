package com.investmanager.api.portfoliohistory.cash;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;

public record CashCheckpointRequest(
    @NotNull LocalDate date,
    @NotNull @DecimalMin("0.00") @Digits(integer=17, fraction=2) BigDecimal balance,
    @NotBlank @Size(max=255) String sourceDescription,
    @AssertTrue boolean confirmedEndOfDayBalance
) {}
