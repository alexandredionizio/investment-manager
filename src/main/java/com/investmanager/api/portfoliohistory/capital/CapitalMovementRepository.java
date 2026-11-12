package com.investmanager.api.portfoliohistory.capital;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CapitalMovementRepository extends JpaRepository<CapitalMovement, Long> {
    List<CapitalMovement> findByPortfolio_IdOrderByMovementDateAscIdAsc(Long portfolioId);
    Optional<CapitalMovement> findByPortfolio_IdAndRequestId(Long portfolioId, UUID requestId);
}
