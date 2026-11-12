package com.investmanager.api.portfoliohistory.capital;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

public record CapitalMovementResponse(Long id, UUID requestId, CapitalMovement.Kind kind,
                                      LocalDate date, BigDecimal amount, String description,
                                      OffsetDateTime createdAt) {
    public static CapitalMovementResponse from(CapitalMovement entity) {
        return new CapitalMovementResponse(entity.getId(), entity.getRequestId(), entity.getKind(),
                entity.getMovementDate(), entity.getAmount(), entity.getDescription(), entity.getCreatedAt());
    }
}
